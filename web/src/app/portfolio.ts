/**
 * Portfolio tab (portfolio-tab 3.1): account header, holdings table, working
 * orders with cancel/modify, and the fill-history section. Every number derives
 * from server state — resync frames plus the journal-backed fills query — so a
 * returning player sees positions, what filled while away, and closed trades.
 */
import type { FillRow, RoundResponse, ServerClient, WirePayload } from '../adapters/serverClient';
import type { LiveOrder, LivePosition, TradeState, VirtualInvestorTradeFeed } from '../adapters/tradeFeed';
import { SECTION_ORDER, describePosition, groupByProduct, holdingSection } from './positions-view';

export interface Portfolio {
  destroy(): void;
}

function money(value: number, digits = 2): string {
  return `${value >= 0 ? '+' : ''}$${value.toFixed(digits)}`;
}

function int(value: number): string {
  return Math.round(value).toLocaleString('en-US');
}

function sideBadge(side: string): string {
  const cls = side === 'SELL' ? 'vi-side-sell' : 'vi-side-buy';
  return `<span class="${cls}">${side}</span>`;
}

function statusPill(status: string): string {
  const cls = status === 'PARTIAL' ? 'vi-st-partial'
    : status === 'WORKING' ? 'vi-st-working' : 'vi-st-done';
  return `<span class="${cls}">${status}</span>`;
}

export function mountPortfolio(
  strip: HTMLElement,
  client: ServerClient,
  round: RoundResponse,
  participant: string,
  tradeFeed: VirtualInvestorTradeFeed,
): Portfolio {
  // Tab shell around the existing positions strip: Positions | Portfolio.
  const dock = document.createElement('section');
  dock.className = 'vi-dock';
  const tabs = document.createElement('div');
  tabs.className = 'vi-tabs';
  tabs.innerHTML = `<button data-tab="positions" class="vi-active">Positions</button>
    <button data-tab="portfolio">Portfolio</button>`;
  const posView = document.createElement('div');
  posView.className = 'vi-tab';
  const pfView = document.createElement('div');
  pfView.className = 'vi-tab';
  pfView.hidden = true;
  pfView.innerHTML = `
    <div class="vi-pf-head"></div>
    <h2>Holdings <span class="vi-pf-count" id="vi-pf-npos"></span></h2>
    <table class="vi-pf-table"><tbody></tbody></table>
    <h2>Working orders <span class="vi-pf-count" id="vi-pf-nord"></span></h2>
    <table class="vi-pf-table"><thead><tr>
      <th>Order</th><th>Side</th><th>Type</th><th class="vi-num">Qty</th><th class="vi-num">Filled</th>
      <th class="vi-num">Price</th><th>Status</th><th></th>
    </tr></thead><tbody></tbody></table>
    <h2>History <span class="vi-pf-count" id="vi-pf-nhist"></span> <button id="vi-pf-reload">Reload</button></h2>
    <table class="vi-pf-table"><thead><tr>
      <th>Order</th><th>Side</th><th class="vi-num">Filled</th><th class="vi-num">Price</th>
      <th class="vi-num">Fee</th><th class="vi-num">Tick</th>
    </tr></thead><tbody></tbody></table>`;
  const parent = strip.parentElement!;
  parent.insertBefore(dock, strip);
  dock.appendChild(tabs);
  posView.appendChild(strip);
  dock.appendChild(posView);
  dock.appendChild(pfView);

  const head = pfView.querySelector('.vi-pf-head') as HTMLElement;
  const holdingsBody = pfView.querySelectorAll('.vi-pf-table tbody')[0] as HTMLElement;
  const ordersBody = pfView.querySelectorAll('.vi-pf-table tbody')[1] as HTMLElement;
  const historyBody = pfView.querySelectorAll('.vi-pf-table tbody')[2] as HTMLElement;

  tabs.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const tab = target.getAttribute('data-tab');
    if (tab === null) {
      return;
    }
    tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('vi-active', b === target));
    posView.hidden = tab !== 'positions';
    pfView.hidden = tab !== 'portfolio';
  });

  // History follows our fills live: a terminal order report means the journal
  // gained rows, so refetch (mount, reconnect and manual reload stay as well).
  const unsubOrders = client.onPayload((payload: WirePayload) => {
    if (payload.type === 'order' && payload.participantId === participant
      && (payload.status === 'FILLED' || payload.status === 'CANCELLED'
        || payload.status === 'REJECTED')) {
      void reloadHistory();
    }
  });

  // Last-trade marks drive unrealized and equity, same as the positions strip.
  const marks = new Map<string, number>();
  const unsubMarks = client.onPayload((payload: WirePayload) => {
    if (payload.type === 'trade') {
      marks.set(String(payload.symbol), Number(payload.price));
      render();
    }
  });

  const closePosition = (symbol: string): void => {
    const position = tradeFeed.state().positions.find((p) => p.symbol === symbol);
    if (position === undefined) {
      return;
    }
    client.placeOrder(round.roundId, participant, {
      clientToken: `pf-close-${symbol}-${Date.now()}`,
      symbol,
      side: position.netQty > 0 ? 'SELL' : 'BUY',
      type: 'MARKET',
      quantity: Math.abs(position.netQty),
    });
  };

  const render = (): void => {
    const state: TradeState = tradeFeed.state();
    let invested = 0;
    let value = 0;
    let unreal = 0;
    for (const p of state.positions) {
      const view = describePosition(p, marks.get(p.symbol) ?? p.avgPrice, NaN);
      invested += view.invested;
      value += view.value;
      unreal += view.unreal;
    }
    const grouped = groupByProduct(state.positions);
    holdingsBody.innerHTML = SECTION_ORDER
      .map((key) => holdingSection(key, grouped.get(key) ?? [], marks))
      .join('') || `<tr><td colspan="11" class="vi-empty">No open positions</td></tr>`;
    setCount('vi-pf-npos', state.positions.length);

    const cash = state.account?.cash ?? 0;
    const locked = state.account?.lockedMargin ?? 0;
    const equity = cash + value;
    const ret = (equity / round.startingCapital - 1) * 100;
    head.innerHTML = `<div class="vi-pf-equity"><label>Equity</label>
      <b>$${int(equity)}</b>
      <span class="${ret >= 0 ? 'vi-up' : 'vi-down'}">${ret >= 0 ? '+' : ''}${ret.toFixed(1)}%</span></div>
      <div class="vi-pf-stat"><label>Cash</label><span>$${int(cash)}</span></div>
      <div class="vi-pf-stat"><label>Buying power</label><span>$${int(cash - locked)}</span></div>
      <div class="vi-pf-stat"><label>Locked</label><span>$${int(locked)}</span></div>
      <div class="vi-pf-stat"><label>Unreal</label>
        <span class="${unreal >= 0 ? 'vi-up' : 'vi-down'}">${money(unreal)}</span></div>
      <div class="vi-pf-stat"><label>Realized</label><span>${money(state.account?.realizedPnl ?? 0)}</span></div>
      <div class="vi-pf-stat"><label>Fees</label><span>${money(state.account?.feesPaid ?? 0)}</span></div>`;

    ordersBody.innerHTML = state.orders.map((o: LiveOrder) => `
      <tr><td class="vi-oid">${o.id.slice(0, 8)}</td><td>${sideBadge(o.side)}</td><td>${o.type}</td>
      <td class="vi-num">${o.qty}</td><td class="vi-num">${o.filledQty}</td>
      <td class="vi-num">${o.price.toFixed(2)}</td><td>${statusPill(o.status)}</td>
      <td class="vi-act"><button data-modify="${o.id}">Modify</button>
      <button data-cancel="${o.id}">Cancel</button></td></tr>`).join('')
      || `<tr><td colspan="8" class="vi-empty">No working orders</td></tr>`;
    setCount('vi-pf-nord', state.orders.length);
  };

  function setCount(id: string, n: number): void {
    const el = pfView.querySelector(`#${id}`);
    if (el !== null) {
      el.textContent = n === 0 ? '' : `(${n})`;
    }
  }

  const reloadHistory = async (): Promise<void> => {
    try {
      const fills: FillRow[] = await client.fills(round.roundId, participant);
      historyBody.innerHTML = fills.map((f) => `<tr><td class="vi-oid">${f.orderId.slice(0, 8)}</td>
        <td>${sideBadge(f.side)}</td><td class="vi-num">${f.filledQuantity}/${f.quantity}</td>
        <td class="vi-num">${Number(f.price).toFixed(2)}</td>
        <td class="vi-num">${money(Number(f.fee))}</td>
        <td class="vi-num">${f.tick}</td></tr>`).join('')
        || `<tr><td colspan="6" class="vi-empty">No fills yet</td></tr>`;
      setCount('vi-pf-nhist', fills.length);
    } catch {
      historyBody.innerHTML = `<tr><td colspan="6" class="vi-empty">History unavailable</td></tr>`;
    }
  };
  void reloadHistory();

  pfView.querySelector('#vi-pf-reload')!.addEventListener('click', () => {
    void reloadHistory();
  });
  pfView.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const close = target.getAttribute('data-close');
    if (close !== null) {
      closePosition(close);
      return;
    }
    const cancel = target.getAttribute('data-cancel');
    if (cancel !== null) {
      client.cancelOrder(cancel);
      return;
    }
    const modify = target.getAttribute('data-modify');
    if (modify !== null) {
      const price = window.prompt('New limit price:');
      if (price !== null && price !== '' && Number.isFinite(Number(price))) {
        client.modifyOrder(modify, Number(price));
      }
    }
  });

  const unsubscribeTrades = tradeFeed.onChange(render);
  const stopReconnect = client.onReconnect(() => {
    void reloadHistory();
  });
  render();

  return {
    destroy() {
      stopReconnect();
      unsubscribeTrades();
      unsubMarks();
      unsubOrders();
      dock.replaceWith(strip);
      dock.remove();
    },
  };
}
