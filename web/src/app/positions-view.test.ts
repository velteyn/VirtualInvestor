import { describe, expect, it } from 'vitest';
import {
  SECTION_ORDER,
  classify,
  describePosition,
  groupByProduct,
  holdingSection,
  isSeries,
  sectionOf,
  strikeOf,
} from './positions-view';
import type { LivePosition } from '../adapters/tradeFeed';

const spot = (symbol: string, netQty = 10): LivePosition =>
  ({ symbol, netQty, avgPrice: 100, realized: 0, locked: 0 });

describe('classify', () => {
  it('spots shares and every series kind', () => {
    expect(classify('NVDO')).toBe('SHARES');
    expect(classify('NVDO-F')).toBe('FUTURE');
    expect(classify('NVDO-C640')).toBe('CALL');
    expect(classify('NVDO-P640.5')).toBe('PUT');
    expect(isSeries('NVDO')).toBe(false);
    expect(isSeries('NVDO-F')).toBe(true);
  });

  it('reads strikes', () => {
    expect(strikeOf('NVDO-C640')).toBe(640);
    expect(Number.isNaN(strikeOf('NVDO-F'))).toBe(true);
    expect(Number.isNaN(strikeOf('NVDO'))).toBe(true);
  });

  it('sections in fixed order', () => {
    expect(SECTION_ORDER).toEqual(['Shares', 'Futures', 'Calls', 'Puts']);
    expect(sectionOf('CALL')).toBe('Calls');
  });
});

describe('describePosition', () => {
  it('values shares by notional', () => {
    const view = describePosition(spot('NVDO', 50), 110, 110);
    expect(view.value).toBe(5_500);
    expect(view.invested).toBe(5_000);
    expect(view.unreal).toBe(500);
    expect(view.expiry).toBe(false);
  });

  it('values futures by variation with locked invested', () => {
    const future: LivePosition = { symbol: 'NVDO-F', netQty: 10, avgPrice: 640, realized: 0, locked: 1280 };
    const view = describePosition(future, 650, 650);
    expect(view.value).toBe(100);
    expect(view.invested).toBe(1280);
    expect(view.unreal).toBe(100);
    expect(view.expiry).toBe(true);
  });

  it('splits option intrinsic and time value with moneyness', () => {
    const call: LivePosition = { symbol: 'NVDO-C640', netQty: 5, avgPrice: 12, realized: 0, locked: 0 };
    const itm = describePosition(call, 25, 665);
    expect(itm.intrinsic).toBe(25);
    expect(itm.timeValue).toBe(0);
    expect(itm.itm).toBe(true);
    expect(itm.payoff).toBe(125);

    const otm = describePosition(call, 8, 630);
    expect(otm.intrinsic).toBe(0);
    expect(otm.timeValue).toBe(8);
    expect(otm.itm).toBe(false);
    expect(otm.payoff).toBe(0);
  });

  it('falls back when the underlying never printed', () => {
    const put: LivePosition = { symbol: 'NVDO-P640', netQty: 5, avgPrice: 12, realized: 0, locked: 0 };
    const view = describePosition(put, 12, NaN);
    expect(view.intrinsic).toBe(0);
    expect(view.itm).toBe(false);
  });
});

describe('groupByProduct', () => {
  it('groups mixed books and skips empties', () => {
    const grouped = groupByProduct([
      spot('NVDO'), spot('NVDO-F'), spot('NVDO-C640'), spot('NVDO-P640'),
    ]);
    expect([...grouped.keys()]).toEqual(['Shares', 'Futures', 'Calls', 'Puts']);
    expect(grouped.get('Calls')?.length).toBe(1);
  });

  it('shares-only books yield one section', () => {
    expect([...groupByProduct([spot('NVDO')]).keys()]).toEqual(['Shares']);
    expect(groupByProduct([]).size).toBe(0);
  });
});

describe('holdingSection', () => {
  const marks = new Map([['NVDO', 110], ['NVDO-F', 650], ['NVDO-C640', 25]]);

  it('hides empty sections', () => {
    expect(holdingSection('Futures', [], marks)).toBe('');
  });

  it('renders shares with today’s columns under its header', () => {
    const html = holdingSection('Shares',
      [{ symbol: 'NVDO', netQty: 50, avgPrice: 100, realized: 0, locked: 0 }], marks);
    expect(html).toContain('Shares');
    expect(html).toContain('Unreal %');
    expect(html).toContain('NVDO');
    expect(html).toContain('+50');
  });

  it('renders futures with side, MTM, locked and expiry', () => {
    const html = holdingSection('Futures',
      [{ symbol: 'NVDO-F', netQty: 10, avgPrice: 640, realized: 0, locked: 1280 }], marks);
    expect(html).toContain('Futures');
    expect(html).toContain('LONG');
    expect(html).toContain('• exp');
    expect(html).toContain('$1,280');
    expect(html).toContain('bell');
    expect(html).toContain('MTM');
  });

  it('renders options with split, badge and payoff', () => {
    const html = holdingSection('Calls',
      [{ symbol: 'NVDO-C640', netQty: 5, avgPrice: 12, realized: 0, locked: 0 }],
      new Map([['NVDO', 665]]));
    expect(html).toContain('ITM');
    expect(html).toContain('25.00 + 0.00');
    expect(html).toContain('$125');
    expect(html).toContain('Strike');
  });
});
