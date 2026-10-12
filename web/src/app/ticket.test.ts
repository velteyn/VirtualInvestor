import { describe, expect, it } from 'vitest';
import {
  isSeriesSymbol,
  maxAffordable,
  maxAffordableSeries,
  parseSeriesSymbol,
  previewQuote,
  previewSeries,
  rememberedQty,
  rememberQty,
  touchPriceFor,
  type BankTerms,
} from './ticket';

const BANK: BankTerms = { feePerShare: 0.01, ticketMinimum: 2.0, seriesRate: 0.001, seriesMinimum: 1.0 };

describe('previewQuote', () => {
  it('adds cost and per-share fee', () => {
    const quote = previewQuote(500, 640, 1_000_000, BANK);
    expect(quote.cost).toBe(320_000);
    expect(quote.fee).toBe(5);
    expect(quote.affordable).toBe(true);
  });

  it('binds the ticket minimum', () => {
    const quote = previewQuote(30, 640, 1_000_000, BANK);
    expect(quote.fee).toBe(2.0);
  });

  it('refuses what cash cannot cover', () => {
    const quote = previewQuote(500, 640, 100, BANK);
    expect(quote.affordable).toBe(false);
  });
});

describe('parseSeriesSymbol', () => {
  it('round-trips futures, calls and puts', () => {
    expect(parseSeriesSymbol('NVDO-F')).toMatchObject({ kind: 'FUTURE', underlying: 'NVDO' });
    expect(parseSeriesSymbol('NVDO-C640')).toMatchObject({ kind: 'CALL', underlying: 'NVDO', strike: 640 });
    expect(parseSeriesSymbol('NVDO-P640.5')).toMatchObject({ kind: 'PUT', underlying: 'NVDO', strike: 640.5 });
  });

  it('rejects spots and garbage', () => {
    expect(parseSeriesSymbol('NVDO')).toBeNull();
    expect(parseSeriesSymbol('NOPE')).toBeNull();
    expect(parseSeriesSymbol('NVDO-X640')).toBeNull();
    expect(parseSeriesSymbol('NVDO-C')).toBeNull();
    expect(parseSeriesSymbol('nvdo-f')).toBeNull();
    expect(isSeriesSymbol('NVDO-F')).toBe(true);
    expect(isSeriesSymbol('NVDO')).toBe(false);
  });
});

describe('previewSeries', () => {
  it('approves an affordable future with margin lock', () => {
    const meta = parseSeriesSymbol('NVDO-F', 0.5)!;
    const preview = previewSeries(5, 640, 'BUY', meta, BANK, 100_000, 100_000);
    expect(preview.notional).toBe(3_200);
    expect(preview.fee).toBeCloseTo(3.2, 9);
    expect(preview.lock).toBe(1_600);
    expect(preview.affordable).toBe(true);
  });

  it('refuses futures notional above equity', () => {
    const meta = parseSeriesSymbol('NVDO-F', 0.5)!;
    const preview = previewSeries(500, 640, 'BUY', meta, BANK, 100_000, 100_000);
    expect(preview.affordable).toBe(false);
    expect(preview.reason).toContain('exceeds equity');
  });

  it('refuses futures the margin cannot cover', () => {
    const meta = parseSeriesSymbol('NVDO-F', 0.9)!;
    const preview = previewSeries(100, 640, 'BUY', meta, BANK, 1_000_000, 10_000);
    expect(preview.affordable).toBe(false);
    expect(preview.reason).toContain('margin');
  });

  it('prices a long call by premium', () => {
    const meta = parseSeriesSymbol('NVDO-C640', 0.5)!;
    const affordable = previewSeries(5, 12, 'BUY', meta, BANK, 100_000, 100_000);
    expect(affordable.affordable).toBe(true);
    expect(affordable.notional).toBe(60);
    const broke = previewSeries(5, 12, 'BUY', meta, BANK, 100_000, 10);
    expect(broke.affordable).toBe(false);
    expect(broke.reason).toContain('buying power');
  });

  it('covers short puts on strike exposure', () => {
    const meta = parseSeriesSymbol('NVDO-P640', 0.5)!;
    const blocked = previewSeries(1_000, 5, 'SELL', meta, BANK, 1_000_000, 100_000);
    expect(blocked.affordable).toBe(false);
    expect(blocked.reason).toContain('margin');
    const allowed = previewSeries(10, 5, 'SELL', meta, BANK, 1_000_000, 100_000);
    expect(allowed.affordable).toBe(true);
    expect(allowed.lock).toBe(0.5 * 640 * 10);
  });
});

describe('maxAffordableSeries', () => {
  it('caps futures by notional and margin', () => {
    const meta = parseSeriesSymbol('NVDO-F', 0.5)!;
    expect(maxAffordableSeries('BUY', 640, meta, 100_000, 100_000)).toBe(156);
    expect(maxAffordableSeries('BUY', 640, meta, 100_000, 1_000)).toBe(3);
  });

  it('sizes options by premium and strike cover', () => {
    const call = parseSeriesSymbol('NVDO-C640', 0.5)!;
    expect(maxAffordableSeries('BUY', 12, call, 100_000, 1_000)).toBe(83);
    const put = parseSeriesSymbol('NVDO-P640', 0.5)!;
    expect(maxAffordableSeries('SELL', 5, put, 100_000, 100_000)).toBe(312);
  });

  it('returns zero for unusable prices', () => {
    const meta = parseSeriesSymbol('NVDO-F', 0.5)!;
    expect(maxAffordableSeries('BUY', 0, meta, 100_000, 100_000)).toBe(0);
    expect(maxAffordableSeries('BUY', NaN, meta, 100_000, 100_000)).toBe(0);
  });
});

describe('sizing memory and touch pricing', () => {
  it('remembers quantities per symbol', () => {
    const store = new Map<string, string>();
    const storage = {
      getItem: (key: string) => store.get(key) ?? null,
      setItem: (key: string, value: string) => { store.set(key, value); },
    };
    expect(rememberedQty(storage, 'NVDO', 100)).toBe(100);
    rememberQty(storage, 'NVDO', 50);
    expect(rememberedQty(storage, 'NVDO', 100)).toBe(50);
    expect(rememberedQty(storage, 'NVDO-F', 100)).toBe(100);
  });

  it('prefers the touch, falls back to the mark', () => {
    expect(touchPriceFor('BUY', { bid: 639, ask: 641 }, 640)).toBe(641);
    expect(touchPriceFor('SELL', { bid: 639, ask: 641 }, 640)).toBe(639);
    expect(touchPriceFor('BUY', undefined, 640)).toBe(640);
    expect(touchPriceFor('BUY', undefined, NaN)).toBe(0);
  });

  it('floors whole lots', () => {
    expect(maxAffordable(10_000, 640)).toBe(15);
  });
});
