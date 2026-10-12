/**
 * Shared position-view helpers (terminal-derivatives-ui D1-D4): row
 * classification and per-kind math used by both the portfolio sections and the
 * positions strip, so the two can never disagree. All option math derives
 * client-side from the series symbol plus marks (D2) — no new streams.
 */
import type { LivePosition } from '../adapters/tradeFeed';

export type ProductKind = 'SHARES' | 'FUTURE' | 'CALL' | 'PUT';

const SERIES_PATTERN = /^([A-Z0-9]+)-(F|C(\d+(?:\.\d+)?)|P(\d+(?:\.\d+)?))$/;

export function classify(symbol: string): ProductKind {
  const match = SERIES_PATTERN.exec(symbol);
  if (match === null) {
    return 'SHARES';
  }
  const leg = match[2];
  if (leg === 'F') {
    return 'FUTURE';
  }
  return leg.startsWith('C') ? 'CALL' : 'PUT';
}

export function isSeries(symbol: string): boolean {
  return classify(symbol) !== 'SHARES';
}

/** Strike of a call/put symbol, NaN otherwise. */
export function strikeOf(symbol: string): number {
  const match = SERIES_PATTERN.exec(symbol);
  if (match === null || match[2] === 'F') {
    return NaN;
  }
  const strike = Number(match[2].startsWith('C') ? match[3] : match[4]);
  return Number.isFinite(strike) && strike > 0 ? strike : NaN;
}

export interface PositionView {
  kind: ProductKind;
  /** True for every derivative series (single expiry at the bell). */
  expiry: boolean;
  /** Display value: notional for shares/options, variation for futures. */
  value: number;
  /** Display invested: notional for shares/options, locked margin for futures. */
  invested: number;
  unreal: number;
  /** Intrinsic value per contract (options only, NaN otherwise). */
  intrinsic: number;
  /** Premium above intrinsic (options only, NaN otherwise). */
  timeValue: number;
  /** Null for non-options. */
  itm: boolean | null;
  /** Intrinsic at the current underlying price if held to the bell (options). */
  payoff: number;
}

export function describePosition(
  position: LivePosition,
  last: number,
  underlyingMark: number,
): PositionView {
  const kind = classify(position.symbol);
  const unreal = (last - position.avgPrice) * position.netQty;
  if (kind === 'FUTURE') {
    return {
      kind, expiry: true, value: unreal, invested: position.locked, unreal,
      intrinsic: NaN, timeValue: NaN, itm: null, payoff: 0,
    };
  }
  if (kind === 'SHARES') {
    return {
      kind, expiry: false, value: position.netQty * last,
      invested: Math.abs(position.netQty) * position.avgPrice, unreal,
      intrinsic: NaN, timeValue: NaN, itm: null, payoff: 0,
    };
  }
  const strike = strikeOf(position.symbol);
  // Without an underlying print there is no moneyness signal: treat the series
  // as at-the-money (intrinsic zero, full premium as time value).
  const ref = Number.isFinite(underlyingMark) ? underlyingMark : strike;
  const intrinsic = kind === 'CALL'
    ? Math.max(ref - strike, 0)
    : Math.max(strike - ref, 0);
  const premium = Math.max(last, 0);
  return {
    kind,
    expiry: true,
    value: position.netQty * last,
    invested: Math.abs(position.netQty) * position.avgPrice,
    unreal,
    intrinsic,
    timeValue: Math.max(premium - intrinsic, 0),
    itm: intrinsic > 0,
    payoff: intrinsic * Math.abs(position.netQty),
  };
}

export type SectionKey = 'Shares' | 'Futures' | 'Calls' | 'Puts';

export function sectionOf(kind: ProductKind): SectionKey {
  switch (kind) {
    case 'FUTURE': return 'Futures';
    case 'CALL': return 'Calls';
    case 'PUT': return 'Puts';
    default: return 'Shares';
  }
}

export const SECTION_ORDER: SectionKey[] = ['Shares', 'Futures', 'Calls', 'Puts'];

export function groupByProduct(positions: LivePosition[]): Map<SectionKey, LivePosition[]> {
  const grouped = new Map<SectionKey, LivePosition[]>();
  for (const position of positions) {
    const key = sectionOf(classify(position.symbol));
    const list = grouped.get(key);
    if (list === undefined) {
      grouped.set(key, [position]);
    } else {
      list.push(position);
    }
  }
  return grouped;
}

export const int = (value: number): string =>
  Math.round(value).toLocaleString('en-US', { maximumFractionDigits: 0 });

export const money = (value: number): string =>
  `${value < 0 ? '-' : ''}$${int(Math.abs(value))}`;

/** One holdings row as HTML (pure — the only DOM touchpoint is insertion). */
export function holdingRow(position: LivePosition, last: number, underlyingMark: number): string {
  const view = describePosition(position, last, underlyingMark);
  const qty = `${position.netQty > 0 ? '+' : ''}${position.netQty}`;
  const close = `<td class="vi-act"><button data-close="${position.symbol}">Close</button></td>`;
  const symbolCell = `<td class="vi-sym">${position.symbol}${view.expiry ? ' • exp' : ''}</td>`;
  if (view.kind === 'FUTURE') {
    const side = position.netQty > 0 ? 'LONG' : 'SHORT';
    return `<tr>${symbolCell}<td>${side}</td><td class="vi-num">${qty}</td>`
      + `<td class="vi-num">${position.avgPrice.toFixed(2)}</td><td class="vi-num">${last.toFixed(2)}</td>`
      + `<td class="vi-num ${view.unreal >= 0 ? 'vi-up' : 'vi-down'}">${money(view.unreal)}</td>`
      + `<td class="vi-num">${money(position.locked)}</td><td>bell</td>`
      + `<td class="vi-num">${money(position.realized)}</td>${close}</tr>`;
  }
  if (view.kind === 'CALL' || view.kind === 'PUT') {
    const strike = strikeOf(position.symbol);
    const badge = view.itm === true ? 'ITM' : 'OTM';
    return `<tr>${symbolCell}<td class="vi-num">${strike}</td><td class="vi-num">${qty}</td>`
      + `<td class="vi-num">${position.avgPrice.toFixed(2)}</td><td class="vi-num">${last.toFixed(2)}</td>`
      + `<td class="vi-num">${view.intrinsic.toFixed(2)} + ${view.timeValue.toFixed(2)}</td>`
      + `<td>${badge}</td><td class="vi-num">${money(view.payoff)}</td><td>bell</td>`
      + `<td class="vi-num">${money(position.realized)}</td>${close}</tr>`;
  }
  const pct = view.invested === 0 ? 0 : (view.unreal / view.invested) * 100;
  return `<tr>${symbolCell}<td class="vi-num">${qty}</td>`
    + `<td class="vi-num">${position.avgPrice.toFixed(2)}</td><td class="vi-num">${last.toFixed(2)}</td>`
    + `<td class="vi-num">${int(view.value)}</td><td class="vi-num">${int(view.invested)}</td>`
    + `<td class="vi-num ${view.unreal >= 0 ? 'vi-up' : 'vi-down'}">${money(view.unreal)}</td>`
    + `<td class="vi-num ${view.unreal >= 0 ? 'vi-up' : 'vi-down'}">${pct >= 0 ? '+' : ''}${pct.toFixed(1)}%</td>`
    + `<td class="vi-num">${money(position.realized)}</td>${close}</tr>`;
}

const SECTION_COLUMNS: Record<SectionKey, string> = {
  Shares: `<tr><td>Symbol</td><td class="vi-num">Qty</td><td class="vi-num">Avg</td>`
    + `<td class="vi-num">Last</td><td class="vi-num">Value</td><td class="vi-num">Invested</td>`
    + `<td class="vi-num">Unreal $</td><td class="vi-num">Unreal %</td><td class="vi-num">Realized</td><td></td></tr>`,
  Futures: `<tr><td>Series</td><td>Side</td><td class="vi-num">Qty</td><td class="vi-num">Entry</td>`
    + `<td class="vi-num">Mark</td><td class="vi-num">MTM</td><td class="vi-num">Locked</td>`
    + `<td>Expiry</td><td class="vi-num">Realized</td><td></td></tr>`,
  Calls: `<tr><td>Series</td><td class="vi-num">Strike</td><td class="vi-num">Qty</td>`
    + `<td class="vi-num">Premium</td><td class="vi-num">Mark</td><td class="vi-num">Intr + Time</td>`
    + `<td>Mny</td><td class="vi-num">Payoff</td><td>Expiry</td><td class="vi-num">Realized</td><td></td></tr>`,
  Puts: `<tr><td>Series</td><td class="vi-num">Strike</td><td class="vi-num">Qty</td>`
    + `<td class="vi-num">Premium</td><td class="vi-num">Mark</td><td class="vi-num">Intr + Time</td>`
    + `<td>Mny</td><td class="vi-num">Payoff</td><td>Expiry</td><td class="vi-num">Realized</td><td></td></tr>`,
};

/** One product section as HTML; empty lists render nothing (spec: hidden). */
export function holdingSection(key: SectionKey, positions: LivePosition[],
  marks: Map<string, number>): string {
  if (positions.length === 0) {
    return '';
  }
  const rows = positions.map((p) => {
    const last = marks.get(p.symbol) ?? p.avgPrice;
    const underlying = underlyingOf(p.symbol);
    const umark = underlying === null ? NaN : (marks.get(underlying) ?? NaN);
    return holdingRow(p, last, umark);
  }).join('');
  return `<tr class="vi-section"><td colspan="11">${key}</td></tr>${SECTION_COLUMNS[key]}${rows}`;
}

function underlyingOf(symbol: string): string | null {
  const match = SERIES_PATTERN.exec(symbol);
  return match === null ? null : match[1];
}

export { underlyingOf };
