/**
 * Shared client for the VirtualInvestor server: REST calls (versioned with the
 * X-API-Version header) and one WebSocket that fans out wire payloads to
 * subscribers. The adapters build on this; they never talk to the server directly.
 */

export const API_VERSION = '1';

export interface RoundConfig {
  startingCapital: number;
  durationMinutes: number;
  preset: 'ARCADE' | 'REALISTIC' | 'HARDCORE';
}

export interface RoundResponse {
  roundId: string;
  seed: number;
  startingCapital: number;
  durationMinutes: number;
  preset: string;
  state: string;
  durationTicks: number;
  sessionToken: string;
}

export interface SymbolResponse {
  ticker: string;
  name: string;
  sector: string;
  assetClass: string;
}

export interface WirePayload {
  type: string;
  [key: string]: unknown;
}

type PayloadListener = (payload: WirePayload) => void;

/** REST + WebSocket client. The base URL defaults to the dev server's proxy. */
export class ServerClient {
  private readonly baseUrl: string;
  private socket: WebSocket | null = null;
  private readonly listeners = new Set<PayloadListener>();
  private reconnectTimer: number | null = null;

  constructor(baseUrl = '') {
    this.baseUrl = baseUrl.replace(/\/$/, '');
  }

  async post<T>(path: string, body: unknown): Promise<T> {
    const response = await fetch(this.baseUrl + path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-API-Version': API_VERSION },
      body: JSON.stringify(body),
    });
    if (!response.ok) {
      const text = await response.text().catch(() => '');
      throw new Error(`POST ${path} failed (${response.status})${text ? `: ${text}` : ''}`);
    }
    return (await response.json()) as T;
  }

  createRound(config: RoundConfig): Promise<RoundResponse> {
    return this.post<RoundResponse>('/api/rounds', config);
  }

  searchSymbols(query: string, limit = 20): Promise<SymbolResponse[]> {
    return this.post<SymbolResponse[]>('/api/symbols/search', { query, limit });
  }

  /** Open the streaming socket; reconnects automatically until closed. */
  connect(): void {
    if (this.socket !== null) {
      return;
    }
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(`${scheme}://${window.location.host}/ws`);
    this.socket = socket;
    socket.onmessage = (event) => {
      let payload: WirePayload;
      try {
        payload = JSON.parse(String(event.data)) as WirePayload;
      } catch {
        return;
      }
      for (const listener of this.listeners) {
        listener(payload);
      }
    };
    socket.onclose = () => {
      this.socket = null;
      if (this.reconnectTimer === null) {
        this.reconnectTimer = window.setTimeout(() => {
          this.reconnectTimer = null;
          if (this.listeners.size > 0) {
            this.connect();
          }
        }, 1500);
      }
    };
  }

  onPayload(listener: PayloadListener): () => void {
    this.listeners.add(listener);
    this.connect();
    return () => {
      this.listeners.delete(listener);
    };
  }
}
