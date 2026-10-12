/**
 * The news wire as a dock tab (terminal-sidebar-layout): the same headline
 * stream previously stacked in the sidebar, now beside Positions/Portfolio.
 * Headlines carry release tick + validity; the newest round tick flips expired
 * items to "priced in". Expired news stays readable.
 */
import type { ServerClient, WirePayload } from '../adapters/serverClient';

export interface NewsWire {
  destroy(): void;
}

interface NewsRow {
  newsId: string;
  badge: string;
  headline: string;
  releaseTick: number;
  validityTicks: number;
  hasStory: boolean;
}

export function mountNewsWire(strip: HTMLElement, client: ServerClient): NewsWire | null {
  const dock = strip.parentElement?.parentElement;
  const tabs = dock?.querySelector('.vi-tabs');
  if (dock == null || tabs == null) {
    return null;
  }

  const button = document.createElement('button');
  button.setAttribute('data-tab', 'newswire');
  button.textContent = 'News';
  tabs.appendChild(button);

  const view = document.createElement('div');
  view.className = 'vi-tab';
  view.hidden = true;
  view.innerHTML = `<section class="vi-panel vi-newsfeed"><ul class="vi-rows"></ul></section>`;
  dock.appendChild(view);

  const newsRows = view.querySelector('.vi-newsfeed .vi-rows') as HTMLElement;
  const news: NewsRow[] = [];
  let newestTick = 0;
  const renderNews = (): void => {
    newsRows.innerHTML = news.map((row) => {
      const pricedIn = row.validityTicks > 0 && newestTick > row.releaseTick + row.validityTicks;
      const badge = `${row.badge}${row.hasStory ? ' · full story' : ''}${pricedIn ? ' · priced in' : ''}`;
      return `<li><div class="vi-news-meta">${escape(badge)}</div><div>${escape(row.headline)}</div></li>`;
    }).join('');
  };
  const unsub = client.onPayload((payload: WirePayload) => {
    if (payload.type === 'trade') {
      const tick = Number(payload.tick);
      if (Number.isFinite(tick) && tick > newestTick) {
        newestTick = tick;
        renderNews();
      }
      return;
    }
    if (payload.type !== 'news') {
      return;
    }
    const releaseTick = Number(payload.tick);
    const validityTicks = Number(payload.durationTicks);
    // Same-newsId re-broadcasts (the article body arrived) refresh the row in
    // place instead of duplicating it.
    const newsId = String(payload.newsId ?? '');
    const at = news.findIndex((row) => row.newsId === newsId);
    if (at >= 0) {
      news.splice(at, 1);
    }
    news.unshift({
      newsId,
      badge: `${String(payload.source)} · ${String(payload.scope)}`,
      headline: String(payload.headline),
      releaseTick: Number.isFinite(releaseTick) ? releaseTick : 0,
      validityTicks: Number.isFinite(validityTicks) ? validityTicks : 0,
      hasStory: typeof payload.body === 'string' && payload.body.length > 80,
    });
    news.length = Math.min(news.length, 20);
    renderNews();
  });

  const onTabs = (event: Event): void => {
    const target = event.target as HTMLElement;
    if (target.getAttribute('data-tab') === null) {
      return;
    }
    tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('vi-active', b === target));
    view.hidden = target.getAttribute('data-tab') !== 'newswire';
  };
  tabs.addEventListener('click', onTabs);

  return {
    destroy() {
      tabs.removeEventListener('click', onTabs);
      unsub();
      button.remove();
      view.remove();
    },
  };
}

function escape(text: string): string {
  return text.replace(/[&<>"]/g, (c) => (c === '&' ? '&amp;' : c === '<' ? '&lt;' : c === '>' ? '&gt;' : '&quot;'));
}
