// @vitest-environment happy-dom
import { describe, expect, it, vi } from 'vitest';
import { mountPortfolio } from './portfolio';
import type { ServerClient } from '../adapters/serverClient';
import type { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';

function mount() {
  const root = document.createElement('div');
  const strip = document.createElement('section');
  strip.className = 'vi-positions';
  root.appendChild(strip);
  document.body.appendChild(root);
  const client = {
    onPayload: () => () => undefined,
    onReconnect: () => () => undefined,
    fills: vi.fn(async () => []),
    placeOrder: vi.fn(),
    cancelOrder: vi.fn(),
    modifyOrder: vi.fn(),
  } as unknown as ServerClient;
  const tradeFeed = {
    state: () => ({
      orders: [],
      positions: [{ symbol: 'NVDO', netQty: 10, avgPrice: 640, realized: 0, locked: 0 }],
      account: { cash: 90_000, realizedPnl: 0, feesPaid: 0, lockedMargin: 0 },
    }),
    onChange: () => () => undefined,
  } as unknown as VirtualInvestorTradeFeed;
  const round = { roundId: 'r1', startingCapital: 100_000, preset: 'REALISTIC' } as never;
  const portfolio = mountPortfolio(strip, client, round, 'human', tradeFeed);
  return { root, portfolio };
}

const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

describe('portfolio dock tabs', () => {
  it('switches between Positions and Portfolio views', async () => {
    const { root, portfolio } = mount();
    await flush();
    const tabs = [...root.querySelectorAll<HTMLButtonElement>('[data-tab]')];
    expect(tabs.length).toBeGreaterThanOrEqual(2);
    tabs.find((b) => b.getAttribute('data-tab') === 'portfolio')?.click();
    const views = [...root.querySelectorAll<HTMLElement>('.vi-tab')];
    const visible = views.filter((v) => !v.hidden);
    expect(visible.length).toBe(1);
    expect(visible[0].textContent ?? '').toContain('NVDO');
    portfolio.destroy();
  });
});
