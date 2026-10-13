// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mountPanels } from './panels';
import type { ServerClient } from '../adapters/serverClient';
import type { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';

function mount() {
  const layout = document.createElement('div');
  document.body.appendChild(layout);
  const client = {
    onPayload: () => () => undefined,
    getLeaderboard: vi.fn(async () => [
      { rank: 1, participantId: 'r1', displayName: 'MARK', equity: 110_000, startingCapital: 100_000, returnFraction: 0.1 },
    ]),
    placeOrder: vi.fn(),
  } as unknown as ServerClient;
  const tradeFeed = {
    state: () => ({
      orders: [],
      positions: [
        { symbol: 'NVDO', netQty: 10, avgPrice: 640, realized: 0, locked: 0 },
        { symbol: 'NVDO-F', netQty: 5, avgPrice: 640, realized: 0, locked: 1600 },
      ],
      account: { cash: 90_000, realizedPnl: 0, feesPaid: 0, lockedMargin: 1600 },
    }),
    onChange: () => () => undefined,
  } as unknown as VirtualInvestorTradeFeed;
  const round = { roundId: 'r1', startingCapital: 100_000, preset: 'REALISTIC' } as never;
  const panels = mountPanels(layout, client, round, tradeFeed, {});
  return { layout, panels };
}

const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

describe('panels mount', () => {
  it('renders sidebar sections and strip positions', async () => {
    const { layout, panels } = mount();
    await flush();
    expect(layout.querySelector('.vi-rivals')).not.toBeNull();
    expect(layout.querySelector('.vi-watchlist')).not.toBeNull();
    const strip = layout.textContent ?? '';
    expect(strip).toContain('NVDO');
    expect(strip).toContain('• exp');
    panels.destroy();
  });

  it('shows the locked margin breakout', async () => {
    const { layout, panels } = mount();
    await flush();
    expect(layout.textContent ?? '').toContain('Locked');
    panels.destroy();
  });
});
