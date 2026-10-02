/**
 * `DataFeed` adapter: serves bars, depth and symbol search from the server's
 * REST + WebSocket surface to `openalgo-charts`. History is synthesized
 * client-side for now (the world sends live bars); the live path uses the
 * socket payloads. Depth comes from `depth` frames.
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

/** Deterministic placeholder history so the chart has candles before live bars arrive. */
function seedBars(symbol: string, count: number, intervalSec: number): Bar[] {
  let seed = 0;
  for (const char of symbol) {
    seed = (seed * 31 + char.charCodeAt(0)) >>> 0;
  }
  const random = (): number => {
    seed = (Math.imul(seed, 1664525) + 1013904223) >>> 0;
    return seed / 4294967296;
  };
  const base = 100 + (seed % 300);
  const bars: Bar[] = [];
  const start = Math.floor(Date.now() / 1000 / intervalSec) * intervalSec - count * intervalSec;
  let last = base;
  for (let i = 0; i < count; i++) {
    const open = last;
    const close = Math.max(1, open + (random() - 0.5) * base * 0.01);
    const high = Math.max(open, close) + random() * base * 0.005;
    const low = Math.min(open, close) - random() * base * 0.005;
    bars.push({ time: start + i * intervalSec, open, high, low, close, volume: Math.round(random() * 10000) });
    last = close;
  }
  return bars;
}

const INTERVAL_SECONDS: Record<string, number> = { '1m': 60, '5m': 300, '15m': 900, '1h': 3600, '1d': 86400 };

export class VirtualInvestorDataFeed implements DataFeed {
  constructor(private readonly client: ServerClient) {}

  async getBars(req: BarsRequest): Promise<Bar[]> {
    const intervalSec = INTERVAL_SECONDS[req.interval] ?? 60;
    const count = req.countBack ?? 400;
    return seedBars(req.symbol, Math.max(50, count), intervalSec);
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
          ? raw.map((level) => ({ price: Number((level as { price: number }).price), qty: Number((level as { qty: number }).qty) }))
          : [];
      onDepth({
        bids: toLevels(payload.bids),
        asks: toLevels(payload.asks),
        ltp: Number(payload.ltp),
      });
    });
  }
}
