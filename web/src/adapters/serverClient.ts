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

export interface HistoryBar {
  time: number;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface LeaderboardEntry {
  rank: number;
  participantId: string;
  displayName: string;
  equity: number;
  startingCapital: number;
  returnFraction: number;
}

export interface OrderCommand {
  clientToken: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  type: 'MARKET' | 'LIMIT' | 'STOP' | 'STOP_LIMIT';
  quantity: number;
  price?: number;
  triggerPrice?: number;
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

  /** Warm-up history for a symbol in a running round, as OHLCV bars. */
  getHistory(roundId: string, symbol: string): Promise<HistoryBar[]> {
    return this.post<HistoryBar[]>('/api/rounds/history', { roundId, symbol });
  }

  /** Current standings for a round. */
  getLeaderboard(roundId: string): Promise<LeaderboardEntry[]> {
    return this.post<LeaderboardEntry[]>('/api/rounds/leaderboard', { roundId });
  }

  /** Bind this socket to a round so order commands route to it. */
  joinRound(roundId: string, participantId: string): void {
    this.send({ type: 'joinRound', roundId, participantId });
  }

  /**
   * Place an order over the socket, carrying a client token for idempotency. The order type is
   * sent as `orderType` because the wire discriminator occupies the `type` key.
   */
  placeOrder(roundId: string, participantId: string, order: OrderCommand): void {
    this.joinRound(roundId, participantId);
    this.send({ type: 'placeOrder', clientToken: order.clientToken, symbol: order.symbol,
      side: order.side, orderType: order.type, quantity: order.quantity, price: order.price ?? 0,
      triggerPrice: order.triggerPrice ?? 0 });
  }

  modifyOrder(orderId: string, price: number, triggerPrice = 0): void {
    this.send({ type: 'modifyOrder', clientToken: `mod-${orderId}-${Date.now()}`, orderId, price, triggerPrice });
  }

  cancelOrder(orderId: string): void {
    this.send({ type: 'cancelOrder', clientToken: `cancel-${orderId}-${Date.now()}`, orderId });
  }

  private send(payload: WirePayload): void {
    this.connect();
    if (this.socket !== null && this.socket.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify(payload));
    } else if (this.socket !== null) {
      const socket = this.socket;
      socket.addEventListener('open', () => socket.send(JSON.stringify(payload)), { once: true });
    }
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
