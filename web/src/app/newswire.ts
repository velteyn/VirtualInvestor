/**
 * The single News dock tab (news-tab-merge): the live wire with per-row full
 * stories. Headlines carry release tick + validity; the newest round tick
 * flips expired items to "priced in". Rows with a story body expand to the
 * full article in a popup; the archive loads once so late joiners read the
 * session's story so far. Expired news stays readable.
 */
import type { ArticleRow, ServerClient, WirePayload } from '../adapters/serverClient';
import type { RoundResponse } from '../adapters/serverClient';

export interface NewsWire {
  destroy(): void;
}

export interface NewsRow {
  newsId: string;
  badge: string;
  headline: string;
  releaseTick: number;
  validityTicks: number;
  body: string;
}

/** A row earns the full-story affordance once its body reads like prose. */
export function canExpand(body: string): boolean {
  return typeof body === 'string' && body.length > 80;
}

/**
 * Merge archived articles into wire rows by news id (pure): fills bodies of
 * rows the live stream already showed, prepends archive-only rows, never
 * duplicates. Newest first.
 */
export function mergeHistory(rows: NewsRow[], articles: ArticleRow[]): NewsRow[] {
  const byId = new Map(rows.map((row) => [row.newsId, row]));
  const missing: NewsRow[] = [];
  for (const article of articles) {
    const row = byId.get(article.newsId);
    if (row !== undefined) {
      if (article.body.length > row.body.length) {
        row.body = article.body;
      }
      continue;
    }
    missing.push({
      newsId: article.newsId,
      badge: `${article.kind} · ${(article.tickers ?? []).join(', ') || 'Market'}`,
      headline: article.headline,
      releaseTick: article.tick,
      validityTicks: 0,
      body: article.body,
    });
  }
  return [...missing.reverse(), ...rows];
}

export function mountNewsWire(
  strip: HTMLElement,
  client: ServerClient,
  round: RoundResponse,
): NewsWire | null {
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
  view.innerHTML = `<section class="vi-panel vi-newsfeed"><ul class="vi-rows"></ul></section>
    <div class="vi-story" hidden>
      <div class="vi-story-card" role="dialog" aria-modal="true">
        <button class="vi-story-close" aria-label="Close">×</button>
        <h3 class="vi-story-head"></h3>
        <p class="vi-story-body"></p>
      </div>
    </div>`;
  dock.appendChild(view);

  const newsRows = view.querySelector('.vi-newsfeed .vi-rows') as HTMLElement;
  const story = view.querySelector('.vi-story') as HTMLElement;
  const storyHead = view.querySelector('.vi-story-head') as HTMLElement;
  const storyBody = view.querySelector('.vi-story-body') as HTMLElement;
  const news: NewsRow[] = [];
  let newestTick = 0;

  const openStory = (row: NewsRow): void => {
    storyHead.textContent = row.headline;
    storyBody.textContent = row.body;
    story.hidden = false;
  };
  const closeStory = (): void => {
    story.hidden = true;
  };

  const renderNews = (): void => {
    newsRows.innerHTML = news.map((row) => {
      const pricedIn = row.validityTicks > 0 && newestTick > row.releaseTick + row.validityTicks;
      const badge = `${row.badge}${canExpand(row.body) ? ' · full story' : ''}${pricedIn ? ' · priced in' : ''}`;
      const link = canExpand(row.body)
        ? ` <button class="vi-link" data-story="${escapeAttr(row.newsId)}">full story</button>`
        : '';
      return `<li><div class="vi-news-meta">${escape(badge)}</div>`
        + `<div>${escape(row.headline)}${link}</div></li>`;
    }).join('');
  };

  const reload = async (): Promise<void> => {
    try {
      const merged = mergeHistory(news, await client.articles(round.roundId));
      news.length = 0;
      news.push(...merged.slice(0, 20));
      renderNews();
    } catch {
      // Keep whatever live payloads already arrived; retry on reconnect.
    }
  };
  void reload();

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
    const body = typeof payload.body === 'string' ? payload.body : '';
    const row: NewsRow = {
      newsId,
      badge: `${String(payload.source)} · ${String(payload.scope)}`,
      headline: String(payload.headline),
      releaseTick: Number.isFinite(releaseTick) ? releaseTick : 0,
      validityTicks: Number.isFinite(validityTicks) ? validityTicks : 0,
      body,
    };
    if (at >= 0) {
      news.splice(at, 1);
    }
    news.unshift(row);
    news.length = Math.min(news.length, 20);
    renderNews();
  });
  const stopReconnect = client.onReconnect(() => {
    void reload();
  });

  newsRows.addEventListener('click', (event: Event) => {
    const target = event.target as HTMLElement;
    const newsId = target.getAttribute('data-story');
    if (newsId === null) {
      return;
    }
    const row = news.find((entry) => entry.newsId === newsId);
    if (row !== undefined) {
      openStory(row);
    }
  });
  const onStoryClick = (event: Event): void => {
    const target = event.target as HTMLElement;
    if (target === story || target.classList.contains('vi-story-close')) {
      closeStory();
    }
  };
  story.addEventListener('click', onStoryClick);
  const onKey = (event: KeyboardEvent): void => {
    if (event.key === 'Escape') {
      closeStory();
    }
  };
  window.addEventListener('keydown', onKey);

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
      window.removeEventListener('keydown', onKey);
      unsub();
      stopReconnect();
      button.remove();
      view.remove();
    },
  };
}

function escape(text: string): string {
  return text.replace(/[&<>"]/g, (c) => (c === '&' ? '&amp;' : c === '<' ? '&lt;' : c === '>' ? '&gt;' : '&quot;'));
}

function escapeAttr(text: string): string {
  return escape(text).replace(/'/g, '&#39;');
}
