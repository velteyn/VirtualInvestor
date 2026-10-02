/**
 * Mounts the `openalgo-charts` widget over our adapters. The library owns the
 * entire terminal (charts, DOM ladder, order ticket, positions, news); this
 * file only supplies the feeds and a symbol.
 */
import { createWidget } from 'openalgo-charts/widget';
import type { RoundResponse, ServerClient } from '../adapters/serverClient';
import { VirtualInvestorDataFeed } from '../adapters/dataFeed';
import { VirtualInvestorNewsFeed } from '../adapters/newsFeed';
import { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';

export interface Terminal {
  destroy(): void;
}

export function mountTerminal(root: HTMLElement, client: ServerClient, round: RoundResponse): Terminal {
  const dataFeed = new VirtualInvestorDataFeed(client);
  const tradeFeed = new VirtualInvestorTradeFeed(client);
  const newsFeed = new VirtualInvestorNewsFeed(client);

  const widget = createWidget(root, {
    symbol: 'NVDO',
    exchange: 'VNDX',
    interval: '5m',
    intervals: ['1m', '5m', '15m', '1h', '1d'],
    topbar: false,
    statusline: true,
    persist: false,
  });

  // The library's widget contract is resolved at runtime; the feed objects
  // satisfy DataFeed/OrderFeed/NewsFeed structurally.
  const host = widget as unknown as {
    chart?: { setDataFeed?: (feed: unknown) => void };
    setDataFeed?: (feed: unknown) => void;
  };
  if (typeof host.setDataFeed === 'function') {
    host.setDataFeed(dataFeed);
  } else if (host.chart && typeof host.chart.setDataFeed === 'function') {
    host.chart.setDataFeed(dataFeed);
  }

  const banner = document.createElement('div');
  banner.className = 'vi-session';
  banner.textContent = `Round ${round.roundId} · ${round.preset} · $${round.startingCapital.toLocaleString()} · ${round.durationMinutes}m`;
  root.prepend(banner);

  return {
    destroy() {
      widget.destroy();
      tradeFeed.dispose();
      newsFeed.dispose();
    },
  };
}
