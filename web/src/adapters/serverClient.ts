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

export interface GameListing {
  roundId: string;
  kind: 'room' | 'solo';
  state: string;
  preset: string;
  timeLeftMillis: number;
  seatsTaken: number;
  seatsTotal: number;
  leader: string;
  masterPlayerId: string | null;
  humans: string[];
  startingCapital: number;
  durationMinutes: number;
}

export interface RoomResponse {
  roundId: string;
  state: string;
  seatsTaken: number;
  seatsTotal: number;
  masterPlayerId: string;
  sessionToken: string;
}

export interface JoinResponse {
  roundId: string;
  seat: string;
  state: string;
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

export interface PlayerResponse {
  playerId: string;
  username: string;
  kind: string;
  status: string;
  bankId: string;
}

export interface LoginResponse {
  playerId: string;
  username: string;
  status: string;
  bankId: string;
  sessionToken: string;
  mustChangePassword: boolean;
}

export interface BankResponse {
  bankId: string;
  displayName: string;
  feePerShare: number;
  ticketMinimum: number;
  custodyRate: number;
}

export interface FillRow {
  orderId: string;
  symbol: string;
  side: string;
  orderType: string;
  quantity: number;
  filledQuantity: number;
  price: number;
  fee: number;
  tick: number;
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
  private readonly reconnectListeners = new Set<() => void>();
  private reconnectTimer: number | null = null;
  private lastJoin: { roundId: string; participantId: string } | null = null;
  private loginToken: string | null = null;
  private loginBankId: string | null = null;
  private loginPlayerId: string | null = null;
  private wasConnected = false;

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
    // Void endpoints (204 control/start, empty 200s) carry no JSON body.
    const text = await response.text();
    if (text === '') {
      return undefined as T;
    }
    return JSON.parse(text) as T;
  }

  createRound(config: RoundConfig): Promise<RoundResponse> {
    // Link the round to the logged-in player so joins bind a seat and the
    // purse resets/writes back; anonymous rounds bind nobody.
    return this.post<RoundResponse>('/api/rounds', { ...config, sessionToken: this.loginToken });
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

  /** Per-order fill history for one participant, folded from the round journal. */
  fills(roundId: string, participantId: string): Promise<FillRow[]> {
    return this.post<FillRow[]>('/api/rounds/fills', { roundId, participantId });
  }

  /** Register a new player; the account pends game-master approval before play. */
  register(username: string, password: string, bankId: string): Promise<PlayerResponse> {
    return this.post<PlayerResponse>('/api/players/register', { username, password, bankId });
  }

  /** Log in an approved player, remembering the token for joins. */
  async login(username: string, password: string): Promise<LoginResponse> {
    const response = await this.post<LoginResponse>('/api/players/login', { username, password });
    this.loginToken = response.sessionToken;
    this.loginBankId = response.bankId;
    this.loginPlayerId = response.playerId;
    return response;
  }

  /** The logged-in player's id, for master checks in the lobby. */
  playerId(): string | null {
    return this.loginPlayerId;
  }

  /** Lobby: every live solo session and waiting room. */
  liveGames(): Promise<GameListing[]> {
    return this.post<GameListing[]>('/api/rounds/live', {});
  }

  /** Create a held room; the creator is room master on seat `human`. */
  createRoom(config: RoundConfig): Promise<RoomResponse> {
    return this.post<RoomResponse>('/api/rooms', { ...config, sessionToken: this.loginToken });
  }

  /** Claim the next seat in a waiting room (idempotent for seated players). */
  joinRoom(roundId: string): Promise<JoinResponse> {
    return this.post<JoinResponse>('/api/rooms/join', { roundId, sessionToken: this.loginToken });
  }

  /** Master-start a waiting room (idempotent once running). */
  startRoom(roundId: string): Promise<void> {
    return this.post<void>('/api/rooms/start', { roundId, sessionToken: this.loginToken });
  }

  /** The logged-in player's bank, for fee previews (order-ticket 1.4). */
  bankOfPlayer(): string | null {
    return this.loginBankId;
  }

  /** Fee-schedule banks a registering player can choose from. */
  banks(): Promise<BankResponse[]> {
    return this.post<BankResponse[]>('/api/players/banks', {});
  }

  /** Player-chosen password change; clears the forced-change flag. */
  changePassword(username: string, oldPassword: string, newPassword: string): Promise<void> {
    return this.post<void>('/api/players/change-password', { username, oldPassword, newPassword });
  }

  /**
   * Bind this socket to a round so order commands route to it. The login token is
   * required: only approved players hold one, and the server refuses the bind
   * without it. Stored for rejoin on reconnect.
   */
  joinRound(roundId: string, participantId: string, sessionToken: string | null = null): void {
    const token = sessionToken ?? this.loginToken;
    this.lastJoin = { roundId, participantId };
    // eslint-disable-next-line no-console
    console.info(`[vi] join round=${roundId} as=${participantId} token=${token === null ? 'none' : 'present'}`);
    this.send({ type: 'joinRound', roundId, participantId, sessionToken: token });
  }

  /**
   * Ask the server to replay this participant's working orders, positions and account
   * (task 5.2). Sent automatically on reconnect; call it directly to reconcile on demand.
   */
  resync(): void {
    if (this.lastJoin === null) {
      return;
    }
    // eslint-disable-next-line no-console
    console.info(`[vi] resync round=${this.lastJoin.roundId} as=${this.lastJoin.participantId}`);
    this.send({ type: 'resync', participantId: this.lastJoin.participantId,
      sessionToken: this.loginToken ?? '', lastSeenSeq: 0 });
  }

  /** Fired whenever the socket reconnects after a drop (after rejoin + resync are queued). */
  onReconnect(listener: () => void): () => void {
    this.reconnectListeners.add(listener);
    return () => this.reconnectListeners.delete(listener);
  }

  /**
   * Place an order over the socket, carrying a client token for idempotency. The order type is
   * sent as `orderType` because the wire discriminator occupies the `type` key.
   */
  placeOrder(roundId: string, participantId: string, order: OrderCommand): void {
    this.joinRound(roundId, participantId, this.loginToken);
    // eslint-disable-next-line no-console
    console.info(`[vi] send placeOrder ${order.clientToken} ${order.side} ${order.quantity} ${order.symbol} ${order.type} @${order.price ?? 0}`);
    this.send({ type: 'placeOrder', clientToken: order.clientToken, symbol: order.symbol,
      side: order.side, orderType: order.type, quantity: order.quantity, price: order.price ?? 0,
      triggerPrice: order.triggerPrice ?? 0 });
  }

  modifyOrder(orderId: string, price: number, triggerPrice = 0): void {
    // eslint-disable-next-line no-console
    console.info(`[vi] send modifyOrder ${orderId} -> ${price}`);
    this.send({ type: 'modifyOrder', clientToken: `mod-${orderId}-${Date.now()}`, orderId, price, triggerPrice });
  }

  cancelOrder(orderId: string): void {
    // eslint-disable-next-line no-console
    console.info(`[vi] send cancelOrder ${orderId}`);
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
    const isReconnect = this.wasConnected;
    socket.onopen = () => {
      this.wasConnected = true;
      // eslint-disable-next-line no-console
      console.info(`[vi] socket open${isReconnect ? ' (reconnect)' : ''}`);
      if (isReconnect) {
        // Rebind the fresh socket and replay the snapshot before live frames resume,
        // then let feeds clear their mirrors so the terminal reconciles (task 5.2).
        if (this.lastJoin !== null) {
          socket.send(JSON.stringify({ type: 'joinRound', ...this.lastJoin, sessionToken: this.loginToken }));
          socket.send(JSON.stringify({ type: 'resync',
            participantId: this.lastJoin.participantId, sessionToken: this.loginToken ?? '',
            lastSeenSeq: 0 }));
        }
        for (const listener of this.reconnectListeners) {
          listener();
        }
      }
    };
    socket.onmessage = (event) => {
      let payload: WirePayload;
      try {
        payload = JSON.parse(String(event.data)) as WirePayload;
      } catch {
        return;
      }
      if (payload.type === 'error') {
        // eslint-disable-next-line no-console
        console.warn(`[vi] server error: ${String(payload.code ?? '')} ${String(payload.message ?? '')}`);
      }
      for (const listener of this.listeners) {
        listener(payload);
      }
    };
    socket.onclose = () => {
      // eslint-disable-next-line no-console
      console.info('[vi] socket closed, reconnecting…');
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
