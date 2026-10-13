/**
 * The terminal's surrounding panels: rivals leaderboard, watchlist and the positions
 * strip. On a wide screen rivals and watchlist stack in a full-height right sidebar
 * with the ticket; the news wire lives in the dock tabs. On a narrow (phone) screen
 * everything collapses below the chart as full-width cards. Layout is driven by CSS,
 * so the same markup works on desktop and mobile.
 */
import type { LeaderboardEntry, RoundResponse, ServerClient, WirePayload } from '../adapters/serverClient';
import type { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';
import { describePosition } from './positions-view';

export interface Panels {
  destroy(): void;
}

const WATCH_DEFAULTS = ['NVDO', 'PEAR', 'VOLT', 'TRLL', 'SOCL'];
const WATCH_KEY = 'vi.watchlist.v1';

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
    <section class="vi-panel vi-watchlist"><h2>Watchlist</h2>
      <div class="vi-watch-add"><input type="search" class="vi-watch-q" placeholder="Add symbol…" aria-label="Add watchlist symbol" list="vi-watch-hits" />
      <datalist id="vi-watch-hits"></datalist></div>
      <ul class="vi-rows"></ul></section>`;
  root.appendChild(column);

  const strip = document.createElement('section');
  strip.className = 'vi-positions';
  strip.innerHTML = `<h2>Positions</h2><div class="vi-pos-rows">No open positions</div>
    <div class="vi-account"></div>`;
  root.appendChild(strip);

  const rivalsRows = column.querySelector('.vi-rivals .vi-rows') as HTMLElement;
  const watchRows = column.querySelector('.vi-watchlist .vi-rows') as HTMLElement;
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
  // Searchable: a query box adds any searched symbol (equity or series);
  // the list persists per browser, defaulting to the five majors.
  const loadWatch = (): string[] => {
    try {
      const raw = window.localStorage.getItem(WATCH_KEY);
      if (raw === null) {
        return [...WATCH_DEFAULTS];
      }
      const parsed: unknown = JSON.parse(raw);
      if (!Array.isArray(parsed)) {
        return [...WATCH_DEFAULTS];
      }
      const symbols = parsed.filter((s): s is string => typeof s === 'string' && s.length > 0);
      return symbols.length > 0 ? [...new Set(symbols)] : [...WATCH_DEFAULTS];
    } catch {
      return [...WATCH_DEFAULTS];
    }
  };
  let watchSymbols = loadWatch();
  const saveWatch = (): void => {
    try {
      window.localStorage.setItem(WATCH_KEY, JSON.stringify(watchSymbols));
    } catch {
      /* private mode: list still works for the session */
    }
  };
  let selected = watchSymbols[0] ?? 'NVDO';
  const watchQuery = column.querySelector('.vi-watch-q') as HTMLInputElement;
  const watchHits = column.querySelector('#vi-watch-hits') as HTMLElement;
  const renderWatchlist = (): void => {
    watchRows.innerHTML = watchSymbols.map((symbol) => {
      const price = prices.get(symbol);
      const shown = price === undefined ? '—' : price.toFixed(2);
      const cls = symbol === selected ? 'vi-active' : '';
      return `<li class="${cls}"><button data-symbol="${symbol}"><span>${symbol}</span><span>${shown}</span></button><button data-remove="${symbol}" aria-label="Remove ${symbol}">×</button></li>`;
    }).join('');
  };
  renderWatchlist();
  const addSymbol = (symbol: string): void => {
    const clean = symbol.trim().toUpperCase();
    if (clean === '') {
      return;
    }
    if (!watchSymbols.includes(clean)) {
      watchSymbols = [...watchSymbols, clean];
      saveWatch();
    }
    selected = clean;
    renderWatchlist();
    options.onSelectSymbol?.(clean);
  };
  let queryTimer = 0;
  watchQuery.addEventListener('input', () => {
    window.clearTimeout(queryTimer);
    queryTimer = window.setTimeout(() => {
      const q = watchQuery.value.trim();
      if (q === '') {
        watchHits.innerHTML = '';
        return;
      }
      void client.searchSymbols(q, 8, round.preset).then((matches) => {
        watchHits.innerHTML = matches.map((m) => `<option value="${m.ticker}">${m.name}</option>`).join('');
      }).catch(() => {
        /* keep the last suggestions */
      });
    }, 150);
  });
  watchQuery.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      addSymbol(watchQuery.value);
      watchQuery.value = '';
      watchHits.innerHTML = '';
    }
  });
  watchRows.addEventListener('click', (event) => {
    const target = event.target as HTMLElement;
    const remove = target.closest('button[data-remove]')?.getAttribute('data-remove');
    if (remove !== null && remove !== undefined) {
      watchSymbols = watchSymbols.filter((s) => s !== remove);
      if (watchSymbols.length === 0) {
        watchSymbols = [...WATCH_DEFAULTS];
      }
      saveWatch();
      if (selected === remove) {
        selected = watchSymbols[0] ?? 'NVDO';
      }
      renderWatchlist();
      return;
    }
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
      // Marks move every print: position P&L must tick with the market, not
      // just on fills (terminal-trading-ui: P&L updates on a tick).
      renderPositions();
    }
  }));


  // ── positions + account ────────────────────────────────────────────────
  const renderPositions = (): void => {
    const state = tradeFeed.state();
    if (state.positions.length === 0) {
      posRows.textContent = 'No open positions';
    } else {
      posRows.innerHTML = state.positions.map((position) => {
        const view = describePosition(position, prices.get(position.symbol) ?? position.avgPrice, NaN);
        const tag = view.expiry ? ' • exp' : '';
        const lock = position.locked > 0 ? ` (locked $${position.locked.toFixed(0)})` : '';
        return `<div class="vi-pos"><span>${position.symbol}${tag}</span>
          <span>${position.netQty > 0 ? '+' : ''}${position.netQty} @ ${position.avgPrice.toFixed(2)}${lock}</span>
          <span class="${view.unreal >= 0 ? 'vi-up' : 'vi-down'}">P&L ${view.unreal >= 0 ? '+' : ''}${view.unreal.toFixed(2)}</span>
          <button data-symbol="${position.symbol}">Close</button></div>`;
      }).join('');
    }
    const locked = state.account?.lockedMargin ?? 0;
    accountLine.textContent = state.account
      ? `Cash $${state.account.cash.toLocaleString(undefined, { maximumFractionDigits: 0 })} · Realized $${state.account.realizedPnl.toFixed(0)}${locked > 0 ? ` · Locked $${locked.toFixed(0)}` : ''}`
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
      window.clearTimeout(queryTimer);
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
