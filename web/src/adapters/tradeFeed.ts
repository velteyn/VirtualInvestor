/**
 * `OrderFeed` + `AccountFeed` adapter: the write path and account reads for
 * `openalgo-charts`' trade layer, backed by the server's WebSocket stream. The
 * terminal's `OrderEngine` is optimistic UI; the server's world is
 * authoritative, so every order update here originates from a server frame.
 * Until the order-entry wire handlers land, writes acknowledge optimistically
 * and the server's later `order`/`position` frames reconcile them.
 */
import type {
  AccountSnapshot,
  Execution,
  ModifyPatch,
  Order,
  OrderFeed,
  PlaceRequest,
  Position,
  TradeMode,
} from 'openalgo-charts/trade';
import type { ServerClient, WirePayload } from './serverClient';

interface OrderListener {
  (order: Order, info: { clientToken?: string }): void;
}
interface PositionListener {
  (positions: Position[]): void;
}

/** Only the members the terminal uses are implemented; the rest are declared unsupported. */
export class VirtualInvestorTradeFeed implements OrderFeed {
  readonly features = undefined;

  private readonly orderListeners = new Set<OrderListener>();
  private readonly positionListeners = new Set<PositionListener>();
  private readonly accountListeners = new Set<(snapshot: AccountSnapshot) => void>();
  private readonly cancelUnsubscribes: Array<() => void> = [];
  private counter = 0;

  constructor(
    private readonly client: ServerClient,
    private readonly accountId = 'human',
  ) {
    this.cancelUnsubscribes.push(client.onPayload((payload) => this.onPayload(payload)));
  }

  private onPayload(payload: WirePayload): void {
    if (payload.type === 'order') {
      const order: Order = {
        id: String(payload.orderId),
        symbol: String(payload.symbol),
        side: payload.side === 'SELL' ? 'SELL' : 'BUY',
        type: mapOrderType(payload.type),
        qty: Number(payload.quantity),
        filledQty: Number(payload.filledQty ?? 0),
        price: Number(payload.price ?? 0),
        status: mapStatus(payload.status),
      };
      for (const listener of this.orderListeners) {
        listener(order, { clientToken: undefined });
      }
    } else if (payload.type === 'position') {
      const position: Position = {
        symbol: String(payload.symbol),
        netQty: Number(payload.netQty),
        avgPrice: Number(payload.avgPrice),
      };
      for (const listener of this.positionListeners) {
        listener([position]);
      }
    } else if (payload.type === 'account') {
      const snapshot: AccountSnapshot = {
        accountId: String(payload.participantId),
        mode: 'analyzer',
        asOf: Date.now() / 1000,
        balance: Number(payload.cash),
        realizedPnl: Number(payload.realizedPnl ?? 0),
      };
      for (const listener of this.accountListeners) {
        listener(snapshot);
      }
    }
  }

  async place(req: PlaceRequest & { mode: TradeMode }): Promise<{ orderId: string }> {
    // The server's command handlers commit the order; this optimistic id keeps
    // the engine's state machine moving until the authoritative frame arrives.
    return { orderId: `local-${++this.counter}-${req.symbol}` };
  }

  async modify(_orderId: string, _patch: ModifyPatch): Promise<void> {
    // Modify acknowledgement is server-sourced; nothing to send yet.
  }

  async cancel(_orderId: string): Promise<void> {
    // Cancel acknowledgement is server-sourced; nothing to send yet.
  }

  subscribeOrders(listener?: (orders: Order[]) => void, onUpdate?: OrderListener): () => void {
    const handler: OrderListener = (order, info) => {
      listener?.([order]);
      onUpdate?.(order, info);
    };
    this.orderListeners.add(handler);
    return () => this.orderListeners.delete(handler);
  }

  subscribePositions(listener: PositionListener): () => void {
    this.positionListeners.add(listener);
    return () => this.positionListeners.delete(listener);
  }

  subscribeAccount(_accountId: string, onSnapshot: (snapshot: AccountSnapshot) => void): () => void {
    this.accountListeners.add(onSnapshot);
    return () => this.accountListeners.delete(onSnapshot);
  }

  async getAccountPositions(): Promise<readonly Position[]> {
    return [];
  }

  async getExecutions(): Promise<readonly Execution[]> {
    return [];
  }

  dispose(): void {
    for (const unsubscribe of this.cancelUnsubscribes) {
      unsubscribe();
    }
    this.cancelUnsubscribes.length = 0;
  }
}

function mapOrderType(raw: unknown): Order['type'] {
  switch (raw) {
    case 'LIMIT':
      return 'LIMIT';
    case 'SL':
      return 'SL';
    case 'SL_M':
      return 'SL-M';
    default:
      return 'MARKET';
  }
}

function mapStatus(raw: unknown): Order['status'] {
  switch (raw) {
    case 'PARTIAL':
      return 'partial';
    case 'FILLED':
      return 'filled';
    case 'CANCELLED':
      return 'cancelled';
    case 'REJECTED':
      return 'rejected';
    default:
      return 'working';
  }
}
