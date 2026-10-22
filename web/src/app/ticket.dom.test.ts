// @vitest-environment happy-dom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mountTicket } from './ticket';
import type { ServerClient } from '../adapters/serverClient';
import type { VirtualInvestorTradeFeed } from '../adapters/tradeFeed';

const CHAIN = [
  { ticker: 'NVDO-F', name: 'Nvido Future', sector: 'Tech', assetClass: 'Future', tickSize: 0.01, quantityGrid: 1, referencePrice: 640, initialMarginFraction: 0.5, maintenanceMarginFraction: 0.25 },
  { ticker: 'NVDO-C620', name: 'Call 620', sector: 'Tech', assetClass: 'Option', tickSize: 0.01, quantityGrid: 1, referencePrice: 20, initialMarginFraction: 0.5, maintenanceMarginFraction: 0.25 },
  { ticker: 'NVDO-C640', name: 'Call 640', sector: 'Tech', assetClass: 'Option', tickSize: 0.01, quantityGrid: 1, referencePrice: 5, initialMarginFraction: 0.5, maintenanceMarginFraction: 0.25 },
  { ticker: 'NVDO-P640', name: 'Put 640', sector: 'Tech', assetClass: 'Option', tickSize: 0.01, quantityGrid: 1, referencePrice: 6, initialMarginFraction: 0.5, maintenanceMarginFraction: 0.25 },
];

const BANK = {
  bankId: 'b1', displayName: 'Bank', feePerShare: 0.01, ticketMinimum: 2.0,
  custodyRate: 0, seriesRate: 0.001, seriesMinimum: 1.0,
};

function mount(options: { chainFails?: boolean } = {}) {
  const column = document.createElement('div');
  document.body.appendChild(column);
  const placed: Array<{ roundId: string; participant: string; command: unknown }> = [];
  const client = {
    searchSymbols: vi.fn(async () => CHAIN),
    chain: options.chainFails
      ? vi.fn(async () => { throw new Error('404'); })
      : vi.fn(async () => CHAIN),
    getHistory: vi.fn(async () => [{ time: 1, open: 639, high: 641, low: 638, close: 640.25, volume: 100 }]),
    banks: vi.fn(async () => [BANK]),
    bankOfPlayer: () => 'b1',
    onPayload: () => () => undefined,
    placeOrder: (roundId: string, participant: string, command: unknown) => {
      placed.push({ roundId, participant, command });
    },
  } as unknown as ServerClient;
  const tradeFeed = {
    state: () => ({
      orders: [], positions: [],
      account: { cash: 1_000_000, realizedPnl: 0, feesPaid: 0, lockedMargin: 0 },
    }),
    onChange: () => () => undefined,
  } as unknown as VirtualInvestorTradeFeed;
  const round = { roundId: 'r1', startingCapital: 1_000_000, preset: 'REALISTIC' } as never;
  const ticket = mountTicket(column, client, round, 'human', tradeFeed);
  return { column, ticket, placed, client };
}

afterEach(() => {
  document.body.innerHTML = '';
  vi.useRealTimers();
});

const flush = (): Promise<void> => new Promise((resolve) => setTimeout(resolve, 0));

describe('ticket mount', () => {
  it('opens in Shares mode with the plain form', async () => {
    const { column, ticket } = mount();
    await flush();
    const modes = [...column.querySelectorAll<HTMLButtonElement>('[data-mode]')];
    expect(modes.map((b) => b.getAttribute('data-mode'))).toEqual(['SHARES', 'FUTURES', 'OPTIONS']);
    expect(column.querySelector('#vi-tk-futureline')?.hasAttribute('hidden')).toBe(true);
    expect(column.querySelector('#vi-tk-ladder')?.hasAttribute('hidden')).toBe(true);
    ticket.destroy();
  });

  it('Futures mode shows the future line with leverage', async () => {
    const { column, ticket } = mount();
    await flush();
    const symbolSelect = column.querySelector<HTMLSelectElement>('#vi-tk-symbol');
    if (symbolSelect !== null) {
      symbolSelect.value = 'NVDO';
      symbolSelect.dispatchEvent(new Event('change', { bubbles: true }));
    }
    await flush();
    column.querySelector<HTMLButtonElement>('[data-mode="FUTURES"]')?.click();
    const line = column.querySelector('#vi-tk-futureline')?.textContent ?? '';
    expect(line).toContain('NVDO-F');
    expect(line).toContain('bell');
    ticket.destroy();
  });

  it('Options mode renders the ladder and strike selection transmits the series', async () => {
    const { column, ticket, placed } = mount();
    await flush();
    const symbolSelect = column.querySelector<HTMLSelectElement>('#vi-tk-symbol');
    if (symbolSelect !== null) {
      symbolSelect.value = 'NVDO';
      symbolSelect.dispatchEvent(new Event('change', { bubbles: true }));
    }
    await flush();
    column.querySelector<HTMLButtonElement>('[data-mode="OPTIONS"]')?.click();
    await flush();
    const strikes = [...column.querySelectorAll<HTMLButtonElement>('[data-strike]')];
    expect(strikes.length).toBeGreaterThan(0);
    strikes[0].click();
    const qty = column.querySelector<HTMLInputElement>('#vi-tk-qty');
    if (qty !== null) {
      qty.value = '1';
    }
    column.querySelector<HTMLButtonElement>('#vi-tk-send')?.click();
    expect(placed.length).toBe(1);
    expect((placed[0].command as { symbol: string }).symbol).toMatch(/NVDO-[CP]/);
    ticket.destroy();
  });

  it('mode switching hides unrelated rows', async () => {
    const { column, ticket } = mount();
    await flush();
    const hidden = (sel: string): boolean => {
      const el = column.querySelector(sel) as HTMLElement | null;
      return el !== null && el.hidden;
    };
    column.querySelector<HTMLButtonElement>('[data-mode="FUTURES"]')?.click();
    expect(hidden('[data-series-row]')).toBe(true);
    expect(hidden('#vi-tk-futureline')).toBe(false);
    expect(hidden('#vi-tk-leg')).toBe(true);
    expect(hidden('#vi-tk-ladder')).toBe(true);
    column.querySelector<HTMLButtonElement>('[data-mode="OPTIONS"]')?.click();
    expect(hidden('[data-series-row]')).toBe(true);
    expect(hidden('#vi-tk-futureline')).toBe(true);
    expect(hidden('#vi-tk-leg')).toBe(false);
    expect(hidden('#vi-tk-ladder')).toBe(false);
    column.querySelector<HTMLButtonElement>('[data-mode="SHARES"]')?.click();
    expect(hidden('[data-series-row]')).toBe(false);
    expect(hidden('#vi-tk-futureline')).toBe(true);
    expect(hidden('#vi-tk-leg')).toBe(true);
    expect(hidden('#vi-tk-ladder')).toBe(true);
    ticket.destroy();
  });

  it('Put leg switches the ladder to puts and transmits a put', async () => {    const { column, ticket, placed } = mount();
    await flush();
    const symbolSelect = column.querySelector<HTMLSelectElement>('#vi-tk-symbol');
    if (symbolSelect !== null) {
      symbolSelect.value = 'NVDO';
      symbolSelect.dispatchEvent(new Event('change', { bubbles: true }));
    }
    await flush();
    column.querySelector<HTMLButtonElement>('[data-mode="OPTIONS"]')?.click();
    await flush();
    column.querySelector<HTMLButtonElement>('[data-leg="PUT"]')?.click();
    const strikes = [...column.querySelectorAll<HTMLButtonElement>('[data-strike]')];
    expect(strikes.length).toBeGreaterThan(0);
    expect(strikes.every((b) => (b.getAttribute('data-strike') ?? '').includes('-P'))).toBe(true);
    strikes[0].click();
    const qty = column.querySelector<HTMLInputElement>('#vi-tk-qty');
    if (qty !== null) {
      qty.value = '1';
    }
    column.querySelector<HTMLButtonElement>('#vi-tk-send')?.click();
    expect(placed.length).toBe(1);
    expect((placed[0].command as { symbol: string }).symbol).toContain('-P');
    ticket.destroy();
  });

  it('falls back to search when the chain endpoint is missing', async () => {
    const { column, ticket } = mount({ chainFails: true });
    await flush();
    const symbolSelect = column.querySelector<HTMLSelectElement>('#vi-tk-symbol');
    if (symbolSelect !== null) {
      symbolSelect.value = 'NVDO';
      symbolSelect.dispatchEvent(new Event('change', { bubbles: true }));
    }
    await flush();
    await flush();
    column.querySelector<HTMLButtonElement>('[data-mode="OPTIONS"]')?.click();
    await flush();
    expect(column.querySelectorAll('[data-strike]').length).toBeGreaterThan(0);
    ticket.destroy();
  });

  it('seeds the limit from history with no live prints', async () => {
    const { column, ticket } = mount();
    await flush();
    await flush();
    column.querySelector<HTMLSelectElement>('#vi-tk-symbol')!.value = 'NVDO';
    column.querySelector<HTMLSelectElement>('#vi-tk-symbol')!
      .dispatchEvent(new Event('change', { bubbles: true }));
    await flush();
    await flush();
    column.querySelector<HTMLSelectElement>('#vi-tk-type')!.value = 'LIMIT';
    column.querySelector<HTMLSelectElement>('#vi-tk-type')!
      .dispatchEvent(new Event('change', { bubbles: true }));
    await flush();
    expect(column.querySelector<HTMLInputElement>('#vi-tk-limit')?.value).toBe('640.25');
    ticket.destroy();
  });
});
