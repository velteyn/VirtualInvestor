/**
 * Order/position/account state accumulated from the server's WebSocket stream, for the
 * terminal's trade overlays. The server is authoritative; this adapter only mirrors the
 * `order`/`position`/`account` frames it receives.
 */
import type { ServerClient, WirePayload } from './serverClient';

export interface LiveOrder {
  id: string;
  symbol: string;
  side: 'BUY' | 'SELL';
  type: string;
  qty: number;
  filledQty: number;
  price: number;
  status: string;
}

export interface LivePosition {
  symbol: string;
  netQty: number;
  avgPrice: number;
}

export interface LiveAccount {
  cash: number;
  realizedPnl: number;
}

export interface TradeState {
  orders: LiveOrder[];
  positions: LivePosition[];
  account: LiveAccount | null;
}

/** Mirrors the server's trade frames into a live order/position/account snapshot. */
export class VirtualInvestorTradeFeed {
  private readonly orders = new Map<string, LiveOrder>();
  private readonly positions = new Map<string, LivePosition>();
  private account: LiveAccount | null = null;
  private readonly listeners = new Set<(state: TradeState) => void>();
  private readonly unsubscribe: () => void;

  constructor(private readonly client: ServerClient) {
    this.unsubscribe = client.onPayload((payload) => this.onPayload(payload));
  }

  private onPayload(payload: WirePayload): void {
    if (payload.type === 'order') {
      const id = String(payload.orderId);
      const status = String(payload.status);
      if (status === 'cancelled' || status === 'rejected') {
        this.orders.delete(id);
      } else {
        this.orders.set(id, {
          id,
          symbol: String(payload.symbol),
          side: payload.side === 'SELL' ? 'SELL' : 'BUY',
          type: String(payload.type ?? 'MARKET'),
          qty: Number(payload.quantity ?? 0),
          filledQty: Number(payload.filledQty ?? 0),
          price: Number(payload.price ?? 0),
          status,
        });
      }
      this.emit();
    } else if (payload.type === 'position') {
      const symbol = String(payload.symbol);
      const netQty = Number(payload.netQty);
      if (netQty === 0) {
        this.positions.delete(symbol);
      } else {
        this.positions.set(symbol, { symbol, netQty, avgPrice: Number(payload.avgPrice) });
      }
      this.emit();
    } else if (payload.type === 'account') {
      this.account = { cash: Number(payload.cash), realizedPnl: Number(payload.realizedPnl ?? 0) };
      this.emit();
    }
  }

  state(): TradeState {
    return {
      orders: [...this.orders.values()],
      positions: [...this.positions.values()],
      account: this.account,
    };
  }

  onChange(listener: (state: TradeState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private emit(): void {
    const state = this.state();
    for (const listener of this.listeners) {
      listener(state);
    }
  }

  dispose(): void {
    this.unsubscribe();
  }
}
