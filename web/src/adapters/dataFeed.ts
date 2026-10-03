/**
 * `DataFeed` adapter: serves bars, depth and symbol search from the server's
 * REST + WebSocket surface to `openalgo-charts`. History comes from the round's
 * live warm-up bars; live updates come from `bar` and `depth` frames.
 */
import type {
  Bar,
  BarsRequest,
  DataFeed,
  MarketDepth,
  SymbolMatch,
  SymbolSearchRequest,
  UnsubscribeFn,
} from 'openalgo-charts';
import type { ServerClient, WirePayload } from './serverClient';

export class VirtualInvestorDataFeed implements DataFeed {
  constructor(
    private readonly client: ServerClient,
    private readonly roundId: string,
  ) {}

  async getBars(req: BarsRequest): Promise<Bar[]> {
    const history = await this.client.getHistory(this.roundId, req.symbol);
    return history.map((bar) => ({
      time: bar.time,
      open: bar.open,
      high: bar.high,
      low: bar.low,
      close: bar.close,
      volume: bar.volume,
    }));
  }

  async searchSymbols(request: SymbolSearchRequest): Promise<SymbolMatch[]> {
    const matches = await this.client.searchSymbols(request.query, 20);
    return matches.map((match) => ({ symbol: match.ticker, name: match.name, assetClass: match.assetClass }));
  }

  subscribeBars(req: BarsRequest, onBar: (bar: Bar, meta?: { provisional?: boolean }) => void): UnsubscribeFn {
    return this.client.onPayload((payload: WirePayload) => {
      if (payload.type !== 'bar' || payload.symbol !== req.symbol) {
        return;
      }
      onBar({
        time: Number(payload.time),
        open: Number(payload.open),
        high: Number(payload.high),
        low: Number(payload.low),
        close: Number(payload.close),
        volume: Number(payload.volume ?? 0),
      });
    });
  }

  subscribeDepth(req: BarsRequest, onDepth: (depth: MarketDepth) => void): UnsubscribeFn {
    return this.client.onPayload((payload: WirePayload) => {
      if (payload.type !== 'depth' || payload.symbol !== req.symbol) {
        return;
      }
      const toLevels = (raw: unknown): { price: number; qty: number }[] =>
        Array.isArray(raw)
          ? raw.map((level) => ({
              price: Number((level as { price: number }).price),
              qty: Number((level as { qty: number }).qty),
            }))
          : [];
      onDepth({
        bids: toLevels(payload.bids),
        asks: toLevels(payload.asks),
        ltp: Number(payload.ltp),
      });
    });
  }
}
