/**
 * Mounts the `openalgo-charts` widget over our adapters. The library owns the
 * entire terminal (charts, drawing rail, top bar, panels, order ticket); this
 * file only supplies the feed, the symbol and the intervals.
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

  // The data feed is a creation-time option; passing it here is what makes the
  // chart load bars. The top bar and rail are the terminal's menus.
  const widget = createWidget(root, {
    feed: dataFeed,
    symbol: 'NVDO',
    exchange: 'VNDX',
    interval: '5m',
    intervals: ['1m', '5m', '15m', '1h', '1d'],
    topbar: true,
    statusline: true,
    panels: true,
    persist: false,
  });

  const banner = document.createElement('div');
  banner.className = 'vi-session';
  banner.textContent = `Round ${round.roundId} · ${round.preset} · $${round.startingCapital.toLocaleString()} · ${round.durationMinutes}m`;
  root.appendChild(banner);

  return {
    destroy() {
      widget.destroy();
      tradeFeed.dispose();
      newsFeed.dispose();
    },
  };
}
