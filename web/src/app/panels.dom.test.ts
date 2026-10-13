// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mountPanels } from './panels';
import type { ServerClient } from '../adapters/serverClient';
import type { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';

function mount() {
  const layout = document.createElement('div');
  document.body.appendChild(layout);
  const payloadListeners: Array<(payload: never) => void> = [];
  const client = {
    onPayload: (listener: (payload: never) => void) => {
      payloadListeners.push(listener);
      return () => undefined;
    },
    searchSymbols: vi.fn(async () => [{ ticker: 'NVDO-C640', name: 'Nvido 640 Call' }]),
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
  return { layout, panels, payloadListeners };
}

const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

afterEach(() => {
  window.localStorage.clear();
  document.body.innerHTML = '';
});

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

  it('adds a searched series to the watchlist and persists it', async () => {
    const { layout, panels } = mount();
    await flush();
    const input = layout.querySelector('.vi-watch-q') as HTMLInputElement;
    expect(input).not.toBeNull();
    input.value = 'NVDO-C640';
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    await flush();
    const rows = [...layout.querySelectorAll('.vi-watchlist [data-symbol]')]
      .map((b) => b.getAttribute('data-symbol'));
    expect(rows).toContain('NVDO-C640');
    expect(window.localStorage.getItem('vi.watchlist.v1')).toContain('NVDO-C640');
    panels.destroy();
  });

  it('removes a watchlist row', async () => {
    const { layout, panels } = mount();
    await flush();
    layout.querySelector<HTMLButtonElement>('[data-remove="PEAR"]')?.click();
    const rows = [...layout.querySelectorAll('.vi-watchlist [data-symbol]')]
      .map((b) => b.getAttribute('data-symbol'));
    expect(rows).not.toContain('PEAR');
    panels.destroy();
  });

  it('ticks position P&L on trade prints', async () => {
    const { layout, panels, payloadListeners } = mount();
    await flush();
    const before = layout.querySelector('.vi-positions')?.textContent ?? '';
    expect(before).toContain('P&L');
    for (const listener of payloadListeners) {
      listener({ type: 'trade', symbol: 'NVDO', price: 700 } as never);
    }
    await flush();
    const after = layout.querySelector('.vi-positions')?.textContent ?? '';
    // 10 NVDO @ 640 marked at 700 → +600 unrealized.
    expect(after).toContain('P&L +600.00');
    panels.destroy();
  });
});
