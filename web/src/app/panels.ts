/**
 * The terminal's surrounding panels: rivals leaderboard, watchlist, news wire and the positions
 * strip. On a wide screen they stack in a right-hand column; on a narrow (phone) screen the
 * column collapses below the chart and each panel is a full-width card. Layout is driven by CSS
 * (`vi-layout`), so the same markup works on desktop and mobile.
 */
import type { LeaderboardEntry, RoundResponse, ServerClient, WirePayload } from '../adapters/serverClient';
import type { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';

export interface Panels {
  destroy(): void;
}

const WATCH_SYMBOLS = ['NVDO', 'PEAR', 'VOLT', 'TRLL', 'SOCL'];

export interface PanelsOptions {
  /** Switch the chart to a watchlist symbol (always a valid name). */
  onSelectSymbol?: (symbol: string) => void;
}

export function mountPanels(
  root: HTMLElement,
  client: ServerClient,
  round: RoundResponse,
  tradeFeed: VirtualInvestorTradeFeed,
  options: PanelsOptions = {},
): Panels {
  const column = document.createElement('aside');
  column.className = 'vi-column';
  column.innerHTML = `
    <section class="vi-panel vi-rivals"><h2>Rivals</h2><ol class="vi-rows"></ol></section>
    <section class="vi-panel vi-watchlist"><h2>Watchlist</h2><ul class="vi-rows"></ul></section>
    <section class="vi-panel vi-newsfeed"><h2>News</h2><ul class="vi-rows"></ul></section>`;
  root.appendChild(column);

  const strip = document.createElement('section');
  strip.className = 'vi-positions';
  strip.innerHTML = `<h2>Positions</h2><div class="vi-pos-rows">No open positions</div>
    <div class="vi-account"></div>`;
  root.appendChild(strip);

  const rivalsRows = column.querySelector('.vi-rivals .vi-rows') as HTMLElement;
  const watchRows = column.querySelector('.vi-watchlist .vi-rows') as HTMLElement;
  const newsRows = column.querySelector('.vi-newsfeed .vi-rows') as HTMLElement;
  const posRows = strip.querySelector('.vi-pos-rows') as HTMLElement;
  const accountLine = strip.querySelector('.vi-account') as HTMLElement;

  // Last prints by symbol. No fictional fallbacks: a name with no print yet
  // shows a dash, never a made-up price.
  const prices = new Map<string, number>();
  const unsubscribers: Array<() => void> = [];

  // ── rivals ─────────────────────────────────────────────────────────────
  const refreshRivals = async (): Promise<void> => {
    try {
      const entries = await client.getLeaderboard(round.roundId);
      rivalsRows.innerHTML = entries.map(renderRival).join('');
    } catch {
      /* keep the last good list */
    }
  };
  void refreshRivals();
  const rivalTimer = window.setInterval(refreshRivals, 3000);

  // ── watchlist ──────────────────────────────────────────────────────────
  // Rows are buttons: clicking one switches the chart to that symbol.
  let selected = 'NVDO';
  const renderWatchlist = (): void => {
    watchRows.innerHTML = WATCH_SYMBOLS.map((symbol) => {
      const price = prices.get(symbol);
      const shown = price === undefined ? '—' : price.toFixed(2);
      const cls = symbol === selected ? 'vi-active' : '';
      return `<li class="${cls}"><button data-symbol="${symbol}"><span>${symbol}</span><span>${shown}</span></button></li>`;
    }).join('');
  };
  renderWatchlist();
  watchRows.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const button = target.closest('button[data-symbol]');
    const symbol = button?.getAttribute('data-symbol');
    if (symbol === null || symbol === undefined) {
      return;
    }
    selected = symbol;
    renderWatchlist();
    options.onSelectSymbol?.(symbol);
  });
  unsubscribers.push(client.onPayload((payload: WirePayload) => {
    if (payload.type === 'trade') {
      prices.set(String(payload.symbol), Number(payload.price));
      renderWatchlist();
    }
  }));

  // ── news ───────────────────────────────────────────────────────────────
  // Headlines carry release tick + validity; the newest round tick (seen on
  // trade frames) flips expired items to "priced in" (news-validity 3.1).
  // Expired news stays readable — bots ignore it, humans still learn from it.
  interface NewsRow {
    badge: string;
    headline: string;
    releaseTick: number;
    validityTicks: number;
  }
  const news: NewsRow[] = [];
  let newestTick = 0;
  const renderNews = (): void => {
    newsRows.innerHTML = news.map((row) => {
      const pricedIn = row.validityTicks > 0 && newestTick > row.releaseTick + row.validityTicks;
      const badge = pricedIn ? `${row.badge} · priced in` : row.badge;
      return `<li><div class="vi-news-meta">${escape(badge)}</div><div>${escape(row.headline)}</div></li>`;
    }).join('');
  };
  unsubscribers.push(client.onPayload((payload: WirePayload) => {
    if (payload.type === 'trade') {
      const tick = Number(payload.tick);
      if (Number.isFinite(tick) && tick > newestTick) {
        newestTick = tick;
        renderNews();
      }
      return;
    }
    if (payload.type !== 'news') {
      return;
    }
    const releaseTick = Number(payload.tick);
    const validityTicks = Number(payload.durationTicks);
    news.unshift({
      badge: `${String(payload.source)} · ${String(payload.scope)}`,
      headline: String(payload.headline),
      releaseTick: Number.isFinite(releaseTick) ? releaseTick : 0,
      validityTicks: Number.isFinite(validityTicks) ? validityTicks : 0,
    });
    news.length = Math.min(news.length, 20);
    renderNews();
  }));

  // ── positions + account ────────────────────────────────────────────────
  const renderPositions = (): void => {
    const state = tradeFeed.state();
    if (state.positions.length === 0) {
      posRows.textContent = 'No open positions';
    } else {
      posRows.innerHTML = state.positions.map((position) => {
        const last = prices.get(position.symbol) ?? position.avgPrice;
        const pnl = (last - position.avgPrice) * position.netQty;
        return `<div class="vi-pos"><span>${position.symbol}</span>
          <span>${position.netQty > 0 ? '+' : ''}${position.netQty} @ ${position.avgPrice.toFixed(2)}</span>
          <span class="${pnl >= 0 ? 'vi-up' : 'vi-down'}">P&L ${pnl >= 0 ? '+' : ''}${pnl.toFixed(2)}</span>
          <button data-symbol="${position.symbol}">Close</button></div>`;
      }).join('');
    }
    accountLine.textContent = state.account
      ? `Cash $${state.account.cash.toLocaleString(undefined, { maximumFractionDigits: 0 })} · Realized $${state.account.realizedPnl.toFixed(0)}`
      : '';
  };
  renderPositions();
  unsubscribers.push(tradeFeed.onChange(renderPositions));
  posRows.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const symbol = target.getAttribute('data-symbol');
    if (symbol) {
      // A close is a market order on the opposite side of the position.
      const position = tradeFeed.state().positions.find((p) => p.symbol === symbol);
      if (position) {
        client.placeOrder(round.roundId, 'human', {
          clientToken: `close-${symbol}-${Date.now()}`,
          symbol,
          side: position.netQty > 0 ? 'SELL' : 'BUY',
          type: 'MARKET',
          quantity: Math.abs(position.netQty),
        });
      }
    }
  });

  return {
    destroy() {
      window.clearInterval(rivalTimer);
      for (const unsubscribe of unsubscribers) {
        unsubscribe();
      }
      column.remove();
      strip.remove();
    },
  };
}

function renderRival(entry: LeaderboardEntry): string {
  const pct = (entry.returnFraction * 100).toFixed(1);
  const cls = entry.returnFraction >= 0 ? 'vi-up' : 'vi-down';
  return `<li><span class="vi-rank">${entry.rank}</span><span>${escape(entry.displayName)}</span>
    <span class="${cls}">${entry.returnFraction >= 0 ? '+' : ''}${pct}%</span></li>`;
}

function escape(text: string): string {
  return text.replace(/[&<>"]/g, (c) => (c === '&' ? '&amp;' : c === '<' ? '&lt;' : c === '>' ? '&gt;' : '&quot;'));
}
