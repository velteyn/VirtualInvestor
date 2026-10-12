import { describe, expect, it } from 'vitest';
import {
  canExpand,
  mergeHistory,
  type NewsRow,
} from './newswire';
import type { ArticleRow } from '../adapters/serverClient';

const row = (newsId: string, body = ''): NewsRow => ({
  newsId, badge: 'WIRE', headline: `Head ${newsId}`, releaseTick: 1, validityTicks: 0, body,
});

const article = (newsId: string, body: string): ArticleRow => ({
  newsId, roundId: 'r1', tick: 2, kind: 'EARNINGS', tickers: ['NVDO'],
  headline: `Story ${newsId}`, body, model: 'wire',
});

describe('canExpand', () => {
  it('needs prose-length bodies', () => {
    expect(canExpand('short')).toBe(false);
    expect(canExpand('x'.repeat(81))).toBe(true);
  });
});

describe('mergeHistory', () => {
  it('fills bodies of live rows without duplicating', () => {
    const merged = mergeHistory([row('n1'), row('n2', 'live body ' + 'x'.repeat(100))],
      [article('n1', 'archived body ' + 'y'.repeat(100)), article('n2', 'short')]);
    expect(merged.length).toBe(2);
    expect(merged.find((r) => r.newsId === 'n1')?.body).toContain('archived body');
    expect(merged.find((r) => r.newsId === 'n2')?.body).toContain('live body');
  });

  it('prepends archive-only rows newest-first', () => {
    const merged = mergeHistory([row('n1')],
      [article('n0', 'old body ' + 'z'.repeat(100)), article('n9', 'new body ' + 'z'.repeat(100))]);
    expect(merged.map((r) => r.newsId)).toEqual(['n9', 'n0', 'n1']);
  });

  it('keeps the longer body either way', () => {
    const merged = mergeHistory([row('n1', 'live ' + 'x'.repeat(200))],
      [article('n1', 'archived ' + 'y'.repeat(100))]);
    expect(merged[0].body).toContain('live');
  });
});
