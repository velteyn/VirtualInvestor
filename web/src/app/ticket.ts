/**
 * Order ticket (order-ticket 1.1): the terminal's single transmit path. Side
 * toggle, type selector with type-gated price fields, quantity, Transmit.
 * Sizing memory, touch-prefill pricing, fee preview and the buying-power guard
 * arrive in 1.2-1.4; the structure already exposes the hooks they need.
 */
import type { OrderCommand, RoundResponse, ServerClient, WirePayload } from '../adapters/serverClient';
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
    <label>Symbol
      <select id="vi-tk-symbol">
        ${ORDER_SYMBOLS.map((ticker) => `<option value="${ticker}">${ticker}</option>`).join('')}
      </select>
    </label>
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
    <p id="vi-tk-error" class="vi-error" role="alert"></p>`;
  column.appendChild(section);

  const sideButtons = [...section.querySelectorAll<HTMLButtonElement>('[data-side]')];
  const typeSelect = section.querySelector<HTMLSelectElement>('#vi-tk-type')!;
  const qty = section.querySelector<HTMLInputElement>('#vi-tk-qty')!;
  const limit = section.querySelector<HTMLInputElement>('#vi-tk-limit')!;
  const stop = section.querySelector<HTMLInputElement>('#vi-tk-stop')!;
  const preview = section.querySelector<HTMLDivElement>('#vi-tk-preview')!;
  const send = section.querySelector<HTMLButtonElement>('#vi-tk-send')!;
  const error = section.querySelector<HTMLParagraphElement>('#vi-tk-error')!;

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
  const symbol = (): string => symbolSelect.value;
  const refreshQty = (): void => {
    qty.value = String(rememberedQty(storage, symbol(), 100));
  };
  refreshQty();
  symbolSelect.addEventListener('change', () => {
    refreshQty();
    limitDirty = false;
    stopDirty = false;
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
        qty.value = String(maxAffordable(tradeFeed.state().account?.cash ?? 0, ticketPrice()));
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

  // Fee preview with buying-power guard (order-ticket 1.4): mirrors the server
  // formula so the preview never lies; Transmit disables over buying power.
  let bank: { feePerShare: number; ticketMinimum: number } | null = null;
  void client.banks().then((banks) => {
    const mine = banks.find((b) => b.bankId === client.bankOfPlayer()) ?? banks[0];
    if (mine !== undefined) {
      bank = { feePerShare: mine.feePerShare, ticketMinimum: mine.ticketMinimum };
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
      send.disabled = false;
      return;
    }
    const cash = tradeFeed.state().account?.cash ?? 0;
    if (bank === null) {
      preview.textContent = `${side} ${quantity} ${symbol()} @ ${price.toFixed(2)} = $${(quantity * price).toFixed(0)}`;
      send.disabled = false;
      return;
    }
    const quote = previewQuote(quantity, price, cash, bank);
    const left = cash - quote.total;
    preview.textContent =
      `${side} ${quantity} ${symbol()} @ ${price.toFixed(2)} = $${quote.cost.toFixed(0)}`
      + ` + $${quote.fee.toFixed(2)} fee → $${left.toFixed(0)} left`;
    send.disabled = !quote.affordable;
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
    }
  });

  send.addEventListener('click', () => {
    error.textContent = '';
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
    rememberQty(storage, symbol(), command.quantity);
    ownOrders.add(command.clientToken);
    // eslint-disable-next-line no-console
    console.info(`[vi] transmit ${command.side} ${command.quantity} ${command.symbol} ${command.type}`);
    client.placeOrder(round.roundId, participant, command);
  });

  return {
    prefill(order: { side: 'BUY' | 'SELL'; type: OrderCommand['type']; price?: number; symbol?: string }): void {
      if (order.symbol !== undefined && ORDER_SYMBOLS.includes(order.symbol)) {
        symbolSelect.value = order.symbol;
        refreshQty();
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
      unsubMarks();
      unsubFeed();
      unsubOrders();
      section.remove();
    },
  };
}
