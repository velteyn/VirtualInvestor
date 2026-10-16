/**
 * Order ticket (order-ticket 1.1): the terminal's single transmit path. Side
 * toggle, type selector with type-gated price fields, quantity, Transmit.
 * Sizing memory, touch-prefill pricing, fee preview and the buying-power guard
 * arrive in 1.2-1.4; the structure already exposes the hooks they need.
 */
import type { OrderCommand, RoundResponse, ServerClient, SymbolResponse, WirePayload } from '../adapters/serverClient';
import type { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';
import companies from '../assets/companies.json';

export interface Ticket {
  destroy(): void;
  /** Prefill from a chart gesture (order-ticket 2.1): review, then Transmit. */
  prefill(order: { side: 'BUY' | 'SELL'; type: OrderCommand['type']; price?: number; symbol?: string }): void;
}

export type TicketType = OrderCommand['type'];

/**
 * Every tradable equity: the full universe minus the VNDX benchmark (the
 * index is quoted but not orderable from the ticket). The company tab and the
 * ticket now agree on what can be traded.
 */
const ORDER_SYMBOLS: string[] = (companies as { ticker: string; type: string }[])
  .filter((company) => company.type !== 'INDEX')
  .map((company) => company.ticker);

/** Minimal storage surface so sizing memory is unit-testable without a DOM. */
export interface QtyMemory {
  getItem(key: string): string | null;
  setItem(key: string, value: string): void;
}

const memoryKey = (symbol: string): string => `vi-ticket-qty-${symbol}`;

export function rememberedQty(storage: QtyMemory, symbol: string, fallback: number): number {
  const raw = Number(storage.getItem(memoryKey(symbol)));
  return Number.isFinite(raw) && raw > 0 ? Math.floor(raw) : fallback;
}

export function rememberQty(storage: QtyMemory, symbol: string, qty: number): void {
  storage.setItem(memoryKey(symbol), String(Math.floor(qty)));
}

/**
 * Touch price for a side (order-ticket 1.3): ask for buys, bid for sells,
 * last mark when the book is empty, zero with no data at all.
 */
export function touchPriceFor(
  side: 'BUY' | 'SELL',
  quote: { bid: number; ask: number } | undefined,
  mark: number,
): number {
  if (quote !== undefined) {
    const touchSide = side === 'BUY' ? quote.ask : quote.bid;
    if (Number.isFinite(touchSide)) {
      return touchSide;
    }
  }
  return Number.isFinite(mark) ? mark : 0;
}

/**
 * Preview quote mirroring the server fee formula (order-ticket 1.4):
 * cost = qty x price, fee = max(ticket minimum, per-share x qty).
 */
export interface PreviewQuote {
  cost: number;
  fee: number;
  total: number;
  affordable: boolean;
}

export function previewQuote(
  qty: number,
  price: number,
  cash: number,
  bank: { feePerShare: number; ticketMinimum: number },
): PreviewQuote {
  const cost = qty * price;
  const fee = Math.max(bank.ticketMinimum, bank.feePerShare * qty);
  return { cost, fee, total: cost + fee, affordable: cost + fee <= cash };
}

/** Largest whole lot affordable at the price (order-ticket 1.2 Max button). */
export function maxAffordable(cash: number, price: number): number {
  if (!Number.isFinite(cash) || !Number.isFinite(price) || price <= 0) {
    return 0;
  }
  return Math.max(0, Math.floor(cash / price));
}

export type SeriesKind = 'FUTURE' | 'CALL' | 'PUT';

export interface SeriesMeta {
  kind: SeriesKind;
  underlying: string;
  strike: number;
  initialMarginFraction: number;
}

const SERIES_PATTERN = /^([A-Z0-9]+)-(F|C(\d+(?:\.\d+)?)|P(\d+(?:\.\d+)?))$/;

export function parseSeriesSymbol(symbol: string, initialMarginFraction = 0.2): SeriesMeta | null {
  const match = SERIES_PATTERN.exec(symbol);
  if (match === null) {
    return null;
  }
  const underlying = match[1];
  const leg = match[2];
  if (leg === 'F') {
    return { kind: 'FUTURE', underlying, strike: NaN, initialMarginFraction };
  }
  const strike = Number(leg.startsWith('C') ? match[3] : match[4]);
  if (!Number.isFinite(strike) || strike <= 0) {
    return null;
  }
  return { kind: leg.startsWith('C') ? 'CALL' : 'PUT', underlying, strike, initialMarginFraction };
}

export function isSeriesSymbol(symbol: string): boolean {
  return SERIES_PATTERN.test(symbol);
}

export interface BankTerms {
  feePerShare: number;
  ticketMinimum: number;
  seriesRate: number;
  seriesMinimum: number;
}

export interface SeriesPreview {
  notional: number;
  fee: number;
  lock: number;
  need: number;
  affordable: boolean;
  reason: string;
}

/**
 * Margin/premium preview mirroring the server sizing gate (futures-options):
 * futures lock margin on notional capped at equity; long options pay premium;
 * short options cover strike exposure. Reasons match the server's.
 */
export function previewSeries(
  qty: number,
  price: number,
  side: 'BUY' | 'SELL',
  meta: SeriesMeta,
  bank: BankTerms,
  equity: number,
  buyingPower: number,
): SeriesPreview {
  if (meta.kind === 'FUTURE') {
    const notional = price * qty;
    const fee = Math.max(bank.seriesMinimum, bank.seriesRate * notional);
    const lock = meta.initialMarginFraction * notional;
    if (notional > equity) {
      return { notional, fee, lock, need: lock + fee, affordable: false, reason: 'futures notional exceeds equity' };
    }
    if (lock > buyingPower) {
      return { notional, fee, lock, need: lock + fee, affordable: false, reason: 'insufficient margin for futures order' };
    }
    return { notional, fee, lock, need: lock + fee, affordable: true, reason: '' };
  }
  const premium = price * qty;
  const fee = Math.max(bank.seriesMinimum, bank.seriesRate * premium);
  if (side === 'BUY') {
    if (premium > buyingPower) {
      return { notional: premium, fee, lock: 0, need: premium + fee, affordable: false, reason: 'insufficient buying power for option premium' };
    }
    return { notional: premium, fee, lock: 0, need: premium + fee, affordable: true, reason: '' };
  }
  const lock = meta.initialMarginFraction * meta.strike * qty;
  if (lock > buyingPower) {
    return { notional: premium, fee, lock, need: lock + fee, affordable: false, reason: 'insufficient margin for short option' };
  }
  return { notional: premium, fee, lock, need: lock + fee, affordable: true, reason: '' };
}

/** One strike-ladder row: live mark premium when printed, else the listed reference. */
export interface LadderRow {
  ticker: string;
  strike: number;
  premium: number;
  itm: boolean;
}

export function ladderRows(
  calls: boolean,
  chain: Array<{ ticker: string; strike: number; referencePrice: number }>,
  marks: Map<string, number>,
  underlyingMark: number,
): LadderRow[] {
  return chain
    .filter((entry) => (calls ? entry.ticker.includes('-C') : entry.ticker.includes('-P')))
    .map((entry) => {
      const premium = marks.get(entry.ticker) ?? entry.referencePrice;
      const intrinsic = calls
        ? Math.max(underlyingMark - entry.strike, 0)
        : Math.max(entry.strike - underlyingMark, 0);
      return { ticker: entry.ticker, strike: entry.strike, premium, itm: intrinsic > 0 };
    })
    .sort((a, b) => a.strike - b.strike);
}

export type TicketMode = 'SHARES' | 'FUTURES' | 'OPTIONS';

/** Firing rules for the confirm dialog (fixed v1 thresholds). */
export type ConfirmRule = 'SIZE' | 'MARGIN' | 'OFFMARKET';

export const CONFIRM_SIZE_FRACTION = 0.25;
export const CONFIRM_OFFMARKET_FRACTION = 0.05;
export const TRANSMIT_LOCK_TIMEOUT_MS = 5000;

export function needsConfirm(input: {
  notional: number;
  buyingPower: number;
  margined: boolean;
  offTouchFraction: number;
}): ConfirmRule[] {
  const rules: ConfirmRule[] = [];
  if (input.buyingPower > 0 && input.notional > CONFIRM_SIZE_FRACTION * input.buyingPower) {
    rules.push('SIZE');
  }
  if (input.margined) {
    rules.push('MARGIN');
  }
  if (input.offTouchFraction > CONFIRM_OFFMARKET_FRACTION) {
    rules.push('OFFMARKET');
  }
  return rules;
}

export interface TransmitLock {
  locked: boolean;
  token: string | null;
}

export function lockFor(token: string): TransmitLock {
  return { locked: true, token };
}

/** Release on the lock's own terminal report or its timeout; foreign events keep it. */
export function releaseOn(
  lock: TransmitLock,
  event: 'filled' | 'cancelled' | 'rejected' | 'timeout',
  token: string,
): TransmitLock {
  void event;
  if (!lock.locked || lock.token !== token) {
    return lock;
  }
  return { locked: false, token: null };
}export function maxAffordableSeries(
  side: 'BUY' | 'SELL',
  price: number,
  meta: SeriesMeta,
  equity: number,
  buyingPower: number,
): number {
  if (!Number.isFinite(price) || price <= 0) {
    return 0;
  }
  if (meta.kind === 'FUTURE') {
    const byNotional = Math.floor(equity / price);
    const byMargin = meta.initialMarginFraction > 0
      ? Math.floor(buyingPower / (meta.initialMarginFraction * price))
      : byNotional;
    return Math.max(0, Math.min(byNotional, byMargin));
  }
  if (side === 'BUY') {
    return Math.max(0, Math.floor(buyingPower / price));
  }
  if (!(meta.strike > 0) || meta.initialMarginFraction <= 0) {
    return 0;
  }
  return Math.max(0, Math.floor(buyingPower / (meta.initialMarginFraction * meta.strike)));
}

// Per-page sequence; paired with a millisecond stamp so tokens are unique per
// sender intent across pages and reloads. The server dedupes by token per
// round, so two players (or a reload) reusing bare sequence numbers would
// silently drop each other's orders (game-lobby 4.1).
let ticketCounter = 0;

export function mountTicket(
  column: HTMLElement,
  client: ServerClient,
  round: RoundResponse,
  participant: string,
  tradeFeed: VirtualInvestorTradeFeed,
): Ticket {
  // Last-trade marks for Max sizing and touch-prefill pricing.
  const marks = new Map<string, number>();
  const touch = new Map<string, { bid: number; ask: number }>();
  let renderPreview: () => void = () => undefined;
  const unsubMarks = client.onPayload((payload: WirePayload) => {
    if (payload.type === 'trade') {
      marks.set(String(payload.symbol), Number(payload.price));
      renderPreview();
      renderLadder();
    } else if (payload.type === 'depth' && payload.symbol === symbol()) {
      const bids = payload.bids as Array<{ price: number }> | undefined;
      const asks = payload.asks as Array<{ price: number }> | undefined;
      touch.set(symbol(), {
        bid: bids !== undefined && bids.length > 0 ? Number(bids[0].price) : NaN,
        ask: asks !== undefined && asks.length > 0 ? Number(asks[0].price) : NaN,
      });
      followTouch();
    }
  });

  // Touch-prefill (order-ticket 1.3): clean price fields track the touch —
  // ask for buys, bid for sells — until the player types (dirty) or a fresh
  // chart gesture reprefills (prefill() clears dirty).
  let limitDirty = false;
  let stopDirty = false;
  const touchPrice = (): number => touchPriceFor(side, touch.get(symbol()), marks.get(symbol()) ?? 0);
  const followTouch = (): void => {
    const type = typeSelect.value as TicketType;
    if ((type === 'LIMIT' || type === 'STOP_LIMIT') && !limitDirty) {
      limit.value = String(touchPrice());
    }
    if ((type === 'STOP' || type === 'STOP_LIMIT') && !stopDirty) {
      stop.value = String(touchPrice());
    }
  };
  const section = document.createElement('section');
  section.className = 'vi-panel vi-ticket';
  section.innerHTML = `
    <h2>Ticket</h2>
    <div class="vi-ticket-modes">
      <button data-mode="SHARES" class="vi-active">Shares</button>
      <button data-mode="FUTURES">Futures</button>
      <button data-mode="OPTIONS">Options</button>
    </div>
    <label>Symbol
      <select id="vi-tk-symbol">
        ${ORDER_SYMBOLS.map((ticker) => `<option value="${ticker}">${ticker}</option>`).join('')}
      </select>
    </label>
    <label data-series-row>Series
      <select id="vi-tk-series">
        <option value="">Shares</option>
      </select>
    </label>
    <div id="vi-tk-futureline" class="vi-tk-future" hidden></div>
    <div id="vi-tk-leg" class="vi-ticket-side" hidden>
      <button data-leg="CALL" class="vi-active">Call</button>
      <button data-leg="PUT">Put</button>
    </div>
    <div id="vi-tk-ladder" class="vi-tk-ladder" hidden></div>
    <div class="vi-ticket-side">
      <button data-side="BUY" class="vi-active vi-buy">Buy</button>
      <button data-side="SELL" class="vi-sell">Sell</button>
    </div>
    <label>Type
      <select id="vi-tk-type">
        <option value="MARKET">Market</option>
        <option value="LIMIT">Limit</option>
        <option value="STOP">Stop</option>
        <option value="STOP_LIMIT">Stop-limit</option>
      </select>
    </label>
    <label>Qty <input id="vi-tk-qty" inputmode="numeric" /></label>
    <div class="vi-tk-lots">
      <button data-lot="10">10</button>
      <button data-lot="50">50</button>
      <button data-lot="100">100</button>
      <button data-lot="max">Max</button>
    </div>
    <label data-price="limit">Limit <input id="vi-tk-limit" inputmode="decimal" /></label>
    <label data-price="stop" hidden>Stop <input id="vi-tk-stop" inputmode="decimal" /></label>
    <div id="vi-tk-preview" class="vi-tk-preview"></div>
    <button id="vi-tk-send">Transmit</button>
    <p id="vi-tk-error" class="vi-error" role="alert"></p>
    <div id="vi-tk-confirm" class="vi-confirm" hidden>
      <div class="vi-confirm-card" role="dialog" aria-modal="true">
        <h3>Confirm order</h3>
        <div class="vi-confirm-lines"></div>
        <div class="vi-confirm-reasons"></div>
        <div class="vi-confirm-actions">
          <button id="vi-tk-ok">Confirm</button>
          <button id="vi-tk-no">Cancel</button>
        </div>
      </div>
    </div>`;
  column.appendChild(section);

  const sideButtons = [...section.querySelectorAll<HTMLButtonElement>('[data-side]')];
  const typeSelect = section.querySelector<HTMLSelectElement>('#vi-tk-type')!;
  const qty = section.querySelector<HTMLInputElement>('#vi-tk-qty')!;
  const limit = section.querySelector<HTMLInputElement>('#vi-tk-limit')!;
  const stop = section.querySelector<HTMLInputElement>('#vi-tk-stop')!;
  const preview = section.querySelector<HTMLDivElement>('#vi-tk-preview')!;
  const send = section.querySelector<HTMLButtonElement>('#vi-tk-send')!;
  const error = section.querySelector<HTMLParagraphElement>('#vi-tk-error')!;
  const confirmBox = section.querySelector<HTMLDivElement>('#vi-tk-confirm')!;
  const confirmLines = section.querySelector<HTMLDivElement>('.vi-confirm-lines')!;
  const confirmReasons = section.querySelector<HTMLDivElement>('.vi-confirm-reasons')!;
  const confirmOk = section.querySelector<HTMLButtonElement>('#vi-tk-ok')!;
  const confirmNo = section.querySelector<HTMLButtonElement>('#vi-tk-no')!;

  let side: 'BUY' | 'SELL' = 'BUY';

  // A field the player typed in stops following the touch (order-ticket 1.3).
  limit.addEventListener('input', () => {
    limitDirty = true;
  });
  stop.addEventListener('input', () => {
    stopDirty = true;
  });

  // Sizing memory (order-ticket 1.2): the last used quantity survives reloads;
  // lot buttons fill it in, Max sizes from buying power at the ticket price.
  // Memory, marks and preview all follow the selected symbol.
  const storage: QtyMemory = window.localStorage;
  const symbolSelect = section.querySelector<HTMLSelectElement>('#vi-tk-symbol')!;
  const seriesSelect = section.querySelector<HTMLSelectElement>('#vi-tk-series')!;
  const underlying = (): string => symbolSelect.value;
  /** Margin fraction for the selected series (chain metadata, 20% fallback). */
  const modeButtons = [...section.querySelectorAll<HTMLButtonElement>('[data-mode]')];
  const legButtons = [...section.querySelectorAll<HTMLButtonElement>('[data-leg]')];
  const seriesRow = section.querySelector<HTMLElement>('[data-series-row]')!;
  const futureLine = section.querySelector<HTMLDivElement>('#vi-tk-futureline')!;
  const ladder = section.querySelector<HTMLDivElement>('#vi-tk-ladder')!;
  let mode: TicketMode = 'SHARES';
  let leg: 'CALL' | 'PUT' = 'CALL';
  let ladderPick = '';
  let chainList: Array<{ ticker: string; strike: number; referencePrice: number }> = [];
  const symbol = (): string => {
    if (mode === 'FUTURES') {
      return `${underlying()}-F`;
    }
    if (mode === 'OPTIONS') {
      return ladderPick;
    }
    return seriesSelect.value === '' ? symbolSelect.value : seriesSelect.value;
  };
  const seriesMeta = (): SeriesMeta | null => {
    const picked = symbol();
    if (picked === '') {
      return null;
    }
    return parseSeriesSymbol(picked, chainMargins.get(picked) ?? 0.2);
  };
  /** Equity estimate (cash + unrealized at last marks) and buying power. */
  const funds = (): { equity: number; buyingPower: number } => {
    const state = tradeFeed.state();
    const cash = state.account?.cash ?? 0;
    const locked = state.account?.lockedMargin ?? 0;
    let unreal = 0;
    for (const position of state.positions) {
      const last = marks.get(position.symbol) ?? position.avgPrice;
      unreal += (last - position.avgPrice) * position.netQty;
    }
    return { equity: cash + unreal, buyingPower: cash - locked };
  };
  // Listed chain per underlying: symbol → margin fraction for the preview.
  let chainMargins = new Map<string, number>();
  let chainRefs = new Map<string, number>();
  let chainUnderlying = '';
  let chainOptions = '';
  let pendingSeries = '';
  const renderSeriesOptions = (): void => {
    const keep = pendingSeries !== '' ? pendingSeries : seriesSelect.value;
    seriesSelect.innerHTML = '<option value="">Shares</option>' + chainOptions;
    seriesSelect.value = [...seriesSelect.options].some((o) => o.value === keep) ? keep : '';
    pendingSeries = '';
  };
  const loadChain = (ticker: string): void => {
    chainUnderlying = ticker;
    chainMargins = new Map();
    chainRefs = new Map();
    chainList = [];
    ladderPick = '';
    chainOptions = '';
    renderSeriesOptions();
    renderLadder();
    const applyChain = (matches: SymbolResponse[]): void => {
      if (chainUnderlying !== ticker) {
        return;
      }
      const kindOf = (symbol: string): string => parseSeriesSymbol(symbol)?.kind ?? '';
      const futures = matches.filter((m) => m.assetClass === 'Future');
      const calls = matches.filter((m) => kindOf(m.ticker) === 'CALL');
      const puts = matches.filter((m) => kindOf(m.ticker) === 'PUT');
      chainMargins = new Map(matches.map((m) => [m.ticker, m.initialMarginFraction]));
      chainRefs = new Map(matches.map((m) => [m.ticker, m.referencePrice]));
      chainList = matches
        .map((m) => {
          const meta = parseSeriesSymbol(m.ticker);
          return meta === null || (meta.kind !== 'CALL' && meta.kind !== 'PUT')
            ? null
            : { ticker: m.ticker, strike: meta.strike, referencePrice: m.referencePrice };
        })
        .filter((entry): entry is { ticker: string; strike: number; referencePrice: number } => entry !== null);
      const opt = (m: { ticker: string }): string => {
        const ref = chainRefs.get(m.ticker);
        const hint = ref !== undefined && kindOf(m.ticker) !== 'FUTURE' ? ` (~$${ref.toFixed(2)})` : '';
        return `<option value="${m.ticker}">${m.ticker}${hint}</option>`;
      };
      chainOptions =
        (futures.length > 0 ? `<optgroup label="Futures">${futures.map(opt).join('')}</optgroup>` : '')
        + (calls.length > 0 ? `<optgroup label="Calls">${calls.map(opt).join('')}</optgroup>` : '')
        + (puts.length > 0 ? `<optgroup label="Puts">${puts.map(opt).join('')}</optgroup>` : '');
      renderSeriesOptions();
      renderLadder();
      updateFutureline();
      renderPreview();
    };
    // Older servers predate /symbols/chain: fall back to substring search so
    // the ladder still fills instead of stranding the ticket Shares-only.
    void client.chain(ticker, round.preset).then(applyChain, () => {
      void client.searchSymbols(`${ticker}-`, 30, round.preset).then(applyChain).catch(() => {
        /* series picker stays Shares-only when the chain cannot load */
      });
    });
  };
  const legRow = section.querySelector<HTMLDivElement>('#vi-tk-leg')!;
  /** Seed marks/touch from the last history close when no live print exists
    * yet, so the ticket has prices from the first second (join-price-seed).
    * Seed-only: live prints and depth always win. Touch seeds as last close
    * on both sides — a stand-in until the first real depth frame. */
  const seedMarks = (ticker: string): void => {
    if (marks.has(ticker)) {
      return;
    }
    void client.getHistory(round.roundId, ticker).then((bars) => {
      if (marks.has(ticker) || bars.length === 0) {
        return;
      }
      const close = Number(bars[bars.length - 1].close);
      if (!Number.isFinite(close) || close <= 0) {
        return;
      }
      marks.set(ticker, close);
      if (!touch.has(ticker)) {
        touch.set(ticker, { bid: close, ask: close });
      }
      followTouch();
      renderLadder();
      renderPreview();
    }).catch(() => {
      /* dark until the first print, as today */
    });
  };
  /** Strike ladder for Options mode: live mark premium else reference, ITM badged. */
  const renderLadder = (): void => {
    if (mode !== 'OPTIONS') {
      return;
    }
    const umark = marks.get(underlying()) ?? NaN;
    const rows = ladderRows(leg === 'CALL', chainList, marks, umark);
    if (pendingSeries !== '' && rows.some((row) => row.ticker === pendingSeries)) {
      ladderPick = pendingSeries;
      pendingSeries = '';
    }
    if (rows.length > 0 && !rows.some((row) => row.ticker === ladderPick)) {
      ladderPick = rows[0].ticker;
      const ref = chainRefs.get(ladderPick);
      if (ref !== undefined && Number.isFinite(ref) && ref > 0) {
        limit.value = String(ref);
        limitDirty = true;
      }
    }
    ladder.innerHTML = rows.map((row) => {
      const active = row.ticker === ladderPick ? 'vi-active' : '';
      return `<button data-strike="${row.ticker}" class="${active}">${row.strike}`
        + ` @ ${row.premium.toFixed(2)}${row.itm ? ' ITM' : ''}</button>`;
    }).join('') || '<span class="vi-empty">No strikes listed</span>';
  };
  ladder.addEventListener('click', (event: Event) => {
    const target = event.target as HTMLElement;
    const ticker = target.getAttribute('data-strike');
    if (ticker === null) {
      return;
    }
    ladderPick = ticker;
    refreshQty();
    const ref = chainRefs.get(ticker);
    if (ref !== undefined && Number.isFinite(ref) && ref > 0) {
      limit.value = String(ref);
      limitDirty = true;
    }
    renderLadder();
    renderPreview();
  });
  const updateFutureline = (): void => {
    if (mode !== 'FUTURES') {
      return;
    }
    const fraction = chainMargins.get(symbol()) ?? 0.2;
    const leverage = fraction > 0 ? (1 / fraction).toFixed(1) : '?';
    futureLine.textContent = `${symbol()} · ${(fraction * 100).toFixed(0)}% margin (${leverage}x) · exp bell`;
  };
  const setMode = (next: TicketMode): void => {
    mode = next;
    modeButtons.forEach((b) => b.classList.toggle('vi-active', b.getAttribute('data-mode') === next));
    seriesRow.hidden = next !== 'SHARES';
    futureLine.hidden = next !== 'FUTURES';
    legRow.hidden = next !== 'OPTIONS';
    ladder.hidden = next !== 'OPTIONS';
    limitDirty = false;
    stopDirty = false;
    if (next === 'FUTURES') {
      updateFutureline();
    }
    if (next === 'OPTIONS') {
      renderLadder();
    }
    followTouch();
    renderPreview();
  };
  modeButtons.forEach((button) => button.addEventListener('click', () => {
    const next = button.getAttribute('data-mode') as TicketMode | null;
    if (next !== null) {
      setMode(next);
    }
  }));
  legButtons.forEach((button) => button.addEventListener('click', () => {
    leg = (button.getAttribute('data-leg') ?? 'CALL') as 'CALL' | 'PUT';
    legButtons.forEach((b) => b.classList.toggle('vi-active', b === button));
    ladderPick = '';
    renderLadder();
    renderPreview();
  }));
  const refreshQty = (): void => {
    qty.value = String(rememberedQty(storage, symbol(), 100));
  };
  refreshQty();
  loadChain(underlying());
  seedMarks(underlying());
  symbolSelect.addEventListener('change', () => {
    refreshQty();
    limitDirty = false;
    stopDirty = false;
    followTouch();
    loadChain(underlying());
    seedMarks(underlying());
    renderPreview();
  });
  seriesSelect.addEventListener('change', () => {
    refreshQty();
    limitDirty = false;
    stopDirty = false;
    // Empty series books have no touch: start the limit at the listed reference
    // (intrinsic floor for options) instead of zero so Transmit can proceed.
    if (seriesSelect.value !== '') {
      const ref = chainRefs.get(seriesSelect.value);
      if (ref !== undefined && Number.isFinite(ref) && ref > 0) {
        limit.value = String(ref);
        limitDirty = true;
      }
    }
    followTouch();
    renderPreview();
  });
  const ticketPrice = (): number => {
    const type = typeSelect.value as TicketType;
    if (type === 'LIMIT' || type === 'STOP_LIMIT') {
      return Number(limit.value);
    }
    const mark = marks.get(symbol());
    return mark ?? 0;
  };
  section.querySelectorAll<HTMLButtonElement>('[data-lot]').forEach((button) => {
    button.addEventListener('click', () => {
      const lot = button.getAttribute('data-lot');
      if (lot === 'max') {
        const meta = seriesMeta();
        if (meta === null) {
          qty.value = String(maxAffordable(tradeFeed.state().account?.cash ?? 0, ticketPrice()));
        } else {
          const { equity, buyingPower } = funds();
          qty.value = String(maxAffordableSeries(side, ticketPrice(), meta, equity, buyingPower));
        }
      } else {
        qty.value = lot ?? '100';
      }
    });
  });

  const gatePrices = (): void => {
    const type = typeSelect.value as TicketType;
    const needsLimit = type === 'LIMIT' || type === 'STOP_LIMIT';
    const needsStop = type === 'STOP' || type === 'STOP_LIMIT';
    section.querySelector<HTMLElement>('[data-price="limit"]')!.hidden = !needsLimit;
    section.querySelector<HTMLElement>('[data-price="stop"]')!.hidden = !needsStop;
  };

  sideButtons.forEach((button) => button.addEventListener('click', () => {
    side = button.getAttribute('data-side') as 'BUY' | 'SELL';
    sideButtons.forEach((b) => b.classList.toggle('vi-active', b === button));
    followTouch();
    renderPreview();
  }));
  typeSelect.addEventListener('change', () => {
    gatePrices();
    followTouch();
    renderPreview();
  });
  gatePrices();

  const read = (): OrderCommand | null => {
    const quantity = Math.floor(Number(qty.value));
    if (!Number.isFinite(quantity) || quantity <= 0) {
      error.textContent = 'quantity must be a positive whole number';
      return null;
    }
    if (mode === 'OPTIONS' && ladderPick === '') {
      error.textContent = 'pick a strike from the ladder';
      return null;
    }
    const type = typeSelect.value as TicketType;
    const command: OrderCommand = {
      clientToken: `tk-${Date.now().toString(36)}-${++ticketCounter}`,
      symbol: symbol(),
      side,
      type,
      quantity,
    };
    if (type === 'LIMIT' || type === 'STOP_LIMIT') {
      const price = Number(limit.value);
      if (!Number.isFinite(price) || price <= 0) {
        error.textContent = 'limit price must be positive';
        return null;
      }
      command.price = price;
    }
    if (type === 'STOP' || type === 'STOP_LIMIT') {
      const price = Number(stop.value);
      if (!Number.isFinite(price) || price <= 0) {
        error.textContent = 'stop price must be positive';
        return null;
      }
      command.triggerPrice = price;
    }
    return command;
  };

  // Transmit lock (ticket-transmit-safety): the button stays dead from click
  // to the order's terminal report or timeout, so raid-clicks cannot duplicate.
  let lock: TransmitLock = { locked: false, token: null };
  let guardOff = false;
  let lockTimer = 0;
  const updateSend = (): void => {
    send.disabled = lock.locked || guardOff;
  };
  const unlock = (token: string): void => {
    const next = releaseOn(lock, 'filled', token);
    if (next !== lock) {
      lock = next;
      window.clearTimeout(lockTimer);
      updateSend();
    }
  };
  let bank: BankTerms | null = null;
  void client.banks().then((banks) => {
    const mine = banks.find((b) => b.bankId === client.bankOfPlayer()) ?? banks[0];
    if (mine !== undefined) {
      bank = {
        feePerShare: mine.feePerShare,
        ticketMinimum: mine.ticketMinimum,
        seriesRate: mine.seriesRate,
        seriesMinimum: mine.seriesMinimum,
      };
      renderPreview();
    }
  }).catch(() => {
    /* preview falls back to cost without fee */
  });
  renderPreview = (): void => {
    const quantity = Math.floor(Number(qty.value));
    const type = typeSelect.value as TicketType;
    const needsPrice = type !== 'MARKET';
    const price = needsPrice ? Number((type === 'STOP' ? stop : limit).value) : touchPrice();
    if (!Number.isFinite(quantity) || quantity <= 0 || !Number.isFinite(price) || price <= 0) {
      preview.textContent = '';
      guardOff = false;
      updateSend();
      return;
    }
    const cash = tradeFeed.state().account?.cash ?? 0;
    if (bank === null) {
      preview.textContent = `${side} ${quantity} ${symbol()} @ ${price.toFixed(2)} = $${(quantity * price).toFixed(0)}`;
      guardOff = false;
      updateSend();
      return;
    }
    const meta = seriesMeta();
    if (meta !== null) {
      const { equity, buyingPower } = funds();
      const series = previewSeries(quantity, price, side, meta, bank, equity, buyingPower);
      const left = buyingPower - series.need;
      const kindLabel = meta.kind === 'FUTURE' ? 'margin' : side === 'BUY' ? 'premium' : 'margin';
      preview.textContent =
        `${side} ${quantity} ${symbol()} @ ${price.toFixed(2)} = $${series.notional.toFixed(0)}`
        + ` + $${series.fee.toFixed(2)} fee + $${series.lock.toFixed(0)} ${kindLabel} → $${left.toFixed(0)} left`;
      guardOff = !series.affordable;
      updateSend();
      error.textContent = series.affordable ? '' : series.reason;
      return;
    }
    const quote = previewQuote(quantity, price, cash, bank);
    const left = cash - quote.total;
    preview.textContent =
      `${side} ${quantity} ${symbol()} @ ${price.toFixed(2)} = $${quote.cost.toFixed(0)}`
      + ` + $${quote.fee.toFixed(2)} fee → $${left.toFixed(0)} left`;
  guardOff = !quote.affordable;
  updateSend();
    error.textContent = quote.affordable ? '' : `needs $${quote.total.toFixed(0)}, have $${cash.toFixed(0)}`;
  };
  const refresh = (): void => {
    renderPreview();
  };
  [qty, limit, stop].forEach((field) => field.addEventListener('input', refresh));
  const unsubFeed = tradeFeed.onChange(refresh);

  // Rejection reasons (order-ticket 2.2): the server's reason lands on the order
  // frame; show it inline. Typed values stay put for one-edit fixing. Scoped to
  // our own client tokens — the stream carries every participant's reports and
  // bot rejects must never surface as ours.
  const ownOrders = new Set<string>();
  const unsubOrders = client.onPayload((payload: WirePayload) => {
    // Server errors (not-approved, not_joined, rejected commands) are addressed
    // to whoever sent them — always surface, since no one else will.
    if (payload.type === 'error') {
      const message = String(payload.message ?? payload.code ?? 'error');
      error.textContent = message;
      return;
    }
    if (payload.type !== 'order' || typeof payload.orderId !== 'string' || !ownOrders.has(payload.orderId)) {
      return;
    }
    const reason = payload.reason === null || payload.reason === undefined ? '' : String(payload.reason);
    if (reason !== '') {
      error.textContent = reason;
    }
    const status = String(payload.status ?? '');
    if (status === 'FILLED' || status === 'CANCELLED' || status === 'REJECTED') {
      ownOrders.delete(payload.orderId);
      unlock(String(payload.orderId));
    }
  });

  const RULE_LABELS: Record<string, string> = {
    SIZE: 'large order (over 25% of buying power)',
    MARGIN: 'margined position',
    OFFMARKET: 'price far off touch (fat-finger check)',
  };

  /** Economics snapshot for the confirm dialog, mirroring the preview math. */
  const econOf = (command: OrderCommand): {
    notional: number; fee: number; lock: number; left: number; buyingPower: number;
  } => {
    const price = command.price ?? touchPrice();
    const quantity = command.quantity;
    const meta = seriesMeta();
    const { buyingPower } = funds();
    if (meta !== null && bank !== null) {
      const series = previewSeries(quantity, price, command.side, meta, bank,
        funds().equity, buyingPower);
      return {
        notional: series.notional, fee: series.fee, lock: series.lock,
        left: buyingPower - series.need, buyingPower,
      };
    }
    const cost = quantity * price;
    const fee = bank === null ? 0 : Math.max(bank.ticketMinimum, bank.feePerShare * quantity);
    const cash = tradeFeed.state().account?.cash ?? 0;
    return { notional: cost, fee, lock: 0, left: cash - cost - fee, buyingPower: cash };
  };

  const reviewOf = (command: OrderCommand): { rules: string[] } => {
    const price = command.price ?? touchPrice();
    const meta = seriesMeta();
    const { buyingPower } = funds();
    const margined = (meta !== null && meta.kind === 'FUTURE') || command.side === 'SELL';
    const notional = meta === null
      ? price * command.quantity
      : meta.kind === 'FUTURE' || command.side === 'BUY'
        ? price * command.quantity
        : meta.strike * command.quantity;
    const touch = touchPrice();
    const offTouch = command.type !== 'MARKET' && touch > 0 && price > 0
      ? Math.abs(price - touch) / touch
      : 0;
    const rules = needsConfirm({ notional, buyingPower, margined, offTouchFraction: offTouch });
    return { rules };
  };

  const closeDialog = (): void => {
    confirmBox.hidden = true;
  };

  const doTransmit = (command: OrderCommand): void => {
    rememberQty(storage, symbol(), command.quantity);
    ownOrders.add(command.clientToken);
    lock = lockFor(command.clientToken);
    window.clearTimeout(lockTimer);
    lockTimer = window.setTimeout(() => {
      lock = releaseOn(lock, 'timeout', command.clientToken);
      updateSend();
    }, TRANSMIT_LOCK_TIMEOUT_MS);
    updateSend();
    // eslint-disable-next-line no-console
    console.info(`[vi] transmit ${command.side} ${command.quantity} ${command.symbol} ${command.type}`);
    client.placeOrder(round.roundId, participant, command);
  };

  const askConfirm = (command: OrderCommand, rules: string[]): void => {
    const econ = econOf(command);
    const price = command.price ?? touchPrice();
    confirmLines.innerHTML =
      `<div>${command.side} ${command.quantity} ${command.symbol}`
      + ` @ ${Number.isFinite(price) && price > 0 ? price.toFixed(2) : 'market'}</div>`
      + `<div>Notional $${econ.notional.toFixed(0)} + $${econ.fee.toFixed(2)} fee`
      + (econ.lock > 0 ? ` + $${econ.lock.toFixed(0)} locked` : '')
      + ` → $${econ.left.toFixed(0)} left</div>`;
    confirmReasons.innerHTML = rules
      .map((rule) => `<div>• ${RULE_LABELS[rule] ?? rule}</div>`).join('');
    confirmBox.hidden = false;
    const onOk = (): void => {
      confirmOk.removeEventListener('click', onOk);
      closeDialog();
      const fresh = read();
      if (fresh === null) {
        return;
      }
      doTransmit(fresh);
    };
    const onNo = (): void => {
      confirmOk.removeEventListener('click', onOk);
      closeDialog();
    };
    confirmOk.addEventListener('click', onOk);
    confirmNo.addEventListener('click', onNo, { once: true });
  };

  send.addEventListener('click', () => {
    error.textContent = '';
    if (lock.locked) {
      // eslint-disable-next-line no-console
      console.info('[vi] transmit blocked: order in flight');
      return;
    }
    const command = read();
    if (command === null) {
      // eslint-disable-next-line no-console
      console.info('[vi] transmit blocked by validation (see ticket error)');
      return;
    }
    if (send.disabled) {
      // eslint-disable-next-line no-console
      console.info(`[vi] transmit blocked by guard: ${error.textContent}`);
      return;
    }
    const { rules } = reviewOf(command);
    if (rules.length > 0) {
      askConfirm(command, rules);
      return;
    }
    doTransmit(command);
  });
  const onKey = (event: KeyboardEvent): void => {
    if (event.key === 'Escape' && !confirmBox.hidden) {
      closeDialog();
    }
  };
  window.addEventListener('keydown', onKey);

  return {
    prefill(order: { side: 'BUY' | 'SELL'; type: OrderCommand['type']; price?: number; symbol?: string }): void {
      if (order.symbol !== undefined) {
        if (ORDER_SYMBOLS.includes(order.symbol)) {
          symbolSelect.value = order.symbol;
          setMode('SHARES');
          refreshQty();
        } else {
          const meta = parseSeriesSymbol(order.symbol);
          if (meta !== null && ORDER_SYMBOLS.includes(meta.underlying)) {
            symbolSelect.value = meta.underlying;
            pendingSeries = order.symbol;
            if (meta.kind === 'FUTURE') {
              setMode('FUTURES');
            } else {
              leg = meta.kind === 'PUT' ? 'PUT' : 'CALL';
              legButtons.forEach((b) => b.classList.toggle('vi-active',
                b.getAttribute('data-leg') === leg));
              ladderPick = order.symbol;
              setMode('OPTIONS');
            }
            loadChain(meta.underlying);
            refreshQty();
          }
        }
      }
      side = order.side;
      sideButtons.forEach((b) => b.classList.toggle('vi-active', b.getAttribute('data-side') === side));
      typeSelect.value = order.type;
      gatePrices();
      if (order.price !== undefined && Number.isFinite(order.price)) {
        if (order.type === 'STOP') {
          stop.value = String(order.price);
        } else {
          limit.value = String(order.price);
        }
      }
      limitDirty = false;
      stopDirty = false;
      followTouch();
      preview.textContent = '';
      error.textContent = '';
      section.scrollIntoView({ block: 'nearest' });
    },
    destroy() {
      window.removeEventListener('keydown', onKey);
      window.clearTimeout(lockTimer);
      unsubMarks();
      unsubFeed();
      unsubOrders();
      section.remove();
    },
  };
}
