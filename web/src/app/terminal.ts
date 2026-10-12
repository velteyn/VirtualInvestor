/**
 * Mounts the `openalgo-charts` widget over our adapters and drives the trade overlays from the
 * server's streams. The widget owns the chart, chrome and right-click order menu; we supply the
 * data feed, route order intents to the socket, and reconcile the order/position frames into the
 * on-chart overlays via the trade tier's `TradeController`.
 */
import { createWidget } from 'openalgo-charts/widget';
import { addComparison, type Bar, type ComparisonHandle } from 'openalgo-charts';
import { underlyingOf } from './positions-view';
import { TradeController } from 'openalgo-charts/trade';
import type { RoundResponse, ServerClient } from '../adapters/serverClient';
import { VirtualInvestorDataFeed } from '../adapters/dataFeed';
import { VirtualInvestorNewsFeed } from '../adapters/newsFeed';
import { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';
import { mountPanels } from './panels';
import { mountPortfolio } from './portfolio';
import { mountTicket } from './ticket';
import { mountCompany, type Company } from './company';
import { mountNewsWire } from './newswire';

export interface Terminal {
  destroy(): void;
}

const SOLO_PARTICIPANT = 'human';

export function mountTerminal(
  root: HTMLElement,
  client: ServerClient,
  round: RoundResponse,
  participant: string = SOLO_PARTICIPANT,
): Terminal {
  const dataFeed = new VirtualInvestorDataFeed(client, round.roundId);
  const tradeFeed = new VirtualInvestorTradeFeed(client, participant);
  const newsFeed = new VirtualInvestorNewsFeed(client);

  // Join the round so order commands route to it, binding the logged-in player,
  // then pull the current snapshot so balance, positions and working orders
  // are populated on first load instead of waiting for a reconnect.
  client.joinRound(round.roundId, participant);
  client.resync();

  // On reconnect the client re-joins and replays the snapshot; clear the mirror first so
  // the terminal reconciles instead of merging stale with fresh state (task 5.2).
  const stopReconnect = client.onReconnect(() => tradeFeed.reset());

  // The layout grid: chart cell (widget), right column (panels), bottom strip (positions).
  const layout = document.createElement('div');
  layout.className = 'vi-terminal';
  const chartCell = document.createElement('div');
  chartCell.className = 'vi-chart';
  layout.appendChild(chartCell);
  root.appendChild(layout);

  // Chart gestures prefill the ticket for review (order-ticket 2.1) — nothing
  // transmits except the ticket's Transmit button. Assigned after mount below.
  let prefillTicket: (
    | ((order: { side: 'BUY' | 'SELL'; type: string; price?: number; symbol?: string }) => void)
    | null
  ) = null;
  const widget = createWidget(chartCell, {
    feed: dataFeed,
    symbol: 'NVDO',
    exchange: 'VNDX',
    interval: '5m',
    intervals: ['1m', '5m', '15m', '1h', '1d'],
    topbar: true,
    statusline: true,
    panels: true,
    persist: false,
    mobile: 'auto',
    onOrder: (order) => {
      prefillTicket?.({
        side: order.side,
        type: order.type,
        price: order.price ?? undefined,
      });
    },
  });

  // Trade overlays: working-order lines, position/P&L, brackets, driven by the stream.
  const trades = new TradeController(widget.chart.tradeHost(0));
  const syncState = (): void => {
    const state = tradeFeed.state();
    trades.reconcile(
      state.orders.map((order) => ({
        id: order.id,
        symbol: order.symbol,
        side: order.side,
        type: order.type as never,
        qty: order.qty,
        filledQty: order.filledQty,
        price: order.price,
        status: order.status as never,
      })),
      state.positions.map((position) => ({
        symbol: position.symbol,
        netQty: position.netQty,
        avgPrice: position.avgPrice,
      })),
    );
  };
  const unsubscribeTrades = tradeFeed.onChange(syncState);

  // Underlying overlay (series-chart-overlay): when the charted symbol is a
  // series, its underlying draws as a comparison line behind it — history
  // plus live bars, removed on symbol switch or destroy.
  interface OverlayState {
    symbol: string;
    handle: ComparisonHandle | null;
    unsub: () => void;
    bars: Bar[];
  }
  let overlay: OverlayState | null = null;
  const clearOverlay = (): void => {
    if (overlay !== null) {
      overlay.unsub();
      if (overlay.handle !== null) {
        try {
          overlay.handle.remove();
        } catch {
          /* already gone with a chart teardown */
        }
      }
      overlay = null;
    }
  };
  const syncOverlay = (chartSymbol: string): void => {
    clearOverlay();
    const underlying = underlyingOf(chartSymbol);
    if (underlying === null) {
      return;
    }
    const state: OverlayState = { symbol: chartSymbol, handle: null, unsub: () => undefined, bars: [] };
    overlay = state;
    const ensureHandle = (): void => {
      if (overlay !== state) {
        return;
      }
      if (state.handle === null) {
        if (state.bars.length === 0) {
          return;
        }
        state.handle = addComparison(widget.chart, {
          symbol: underlying, bars: [...state.bars], color: '#8a93a6', type: 'line',
        });
      } else {
        state.handle.setBars([...state.bars]);
      }
    };
    const req = { symbol: underlying, exchange: 'VNDX', interval: '5m' };
    state.unsub = dataFeed.subscribeBars(req, (bar) => {
      const last = state.bars[state.bars.length - 1];
      if (last !== undefined && last.time === bar.time) {
        state.bars[state.bars.length - 1] = bar;
      } else {
        state.bars.push(bar);
      }
      ensureHandle();
    });
    void dataFeed.getBars(req).then((history) => {
      if (overlay !== state) {
        return;
      }
      const firstLive = state.bars.length > 0 ? state.bars[0].time : Infinity;
      state.bars.unshift(...history.filter((bar) => bar.time < firstLive));
      ensureHandle();
    }).catch(() => {
      /* live bars still feed the overlay when history fails */
    });
  };
  // Clicking a watchlist row switches the chart to that symbol (always a
  // valid name, so the chart can never land on an unknown-symbol error) and
  // follows along in the company tab when it is mounted below.
  let company: Company | null = null;
  const panels = mountPanels(layout, client, round, tradeFeed, {
    onSelectSymbol: (symbol: string) => {
      widget.setSymbol(symbol, 'VNDX');
      syncOverlay(symbol);
      company?.show(symbol);
    },
  });

  // Order ticket below news in the right column (order-ticket 1.1).
  const column = layout.querySelector<HTMLElement>('.vi-column');
  const ticket = column === null ? null : mountTicket(column, client, round, participant, tradeFeed);
  prefillTicket = (order) => ticket?.prefill({
    side: order.side,
    type: (order.type === 'SL' ? 'STOP' : order.type) as 'MARKET' | 'LIMIT' | 'STOP' | 'STOP_LIMIT',
    price: order.price,
    symbol: order.symbol ?? 'NVDO',
  });

  // Portfolio tab wraps the positions strip (portfolio-tab 3.1); the
  // company tab joins the same dock once it exists (company-profiles 2.2).
  const strip = layout.querySelector<HTMLElement>('.vi-positions');
  const portfolio = strip === null ? null : mountPortfolio(strip, client, round, participant, tradeFeed);
  company = strip === null || portfolio === null ? null : mountCompany(strip);
  const newswire = strip === null || portfolio === null ? null : mountNewsWire(strip, client, round);

  // A small session banner (kept out of the way on narrow screens).
  const banner = document.createElement('div');
  banner.className = 'vi-session';
  banner.textContent = `Round ${round.roundId} · ${round.preset} · $${round.startingCapital.toLocaleString()} · ${round.durationMinutes}m`;
  document.body.appendChild(banner);

  return {
    destroy() {
      stopReconnect();
      unsubscribeTrades();
      clearOverlay();
      ticket?.destroy();
      company?.destroy();
      newswire?.destroy();
      portfolio?.destroy();
      panels.destroy();
      widget.destroy();
      tradeFeed.dispose();
      newsFeed.dispose();
      banner.remove();
      layout.remove();
    },
  };
}
