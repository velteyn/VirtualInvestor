/**
 * Escape text for interpolation into HTML (element content and
 * double-quoted attributes). Server tickers and player-chosen names flow
 * into innerHTML templates across the terminal, so everything untrusted
 * goes through here — CodeQL flags the sinks otherwise.
 */
export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}
