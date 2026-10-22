import { describe, expect, it } from 'vitest';
import { escapeHtml } from './html';

describe('escapeHtml', () => {
  it('escapes markup-significant characters', () => {
    expect(escapeHtml('<img src=x onerror=alert(1)>'))
      .toBe('&lt;img src=x onerror=alert(1)&gt;');
    expect(escapeHtml('"quoted" & \'apos\'')).toBe('&quot;quoted&quot; &amp; &#39;apos&#39;');
  });

  it('leaves plain tickers untouched', () => {
    expect(escapeHtml('NVDO-C640')).toBe('NVDO-C640');
  });
});
