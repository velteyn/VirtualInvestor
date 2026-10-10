/**
 * Newsroom tab (newsroom-narrator 3.2): the round's articles with their story
 * bodies. History loads once from the articles endpoint (late joiners read
 * the session's story so far); live article payloads upsert by news id, the
 * same convention as the news panel and the chart feed. Mounts into the
 * positions-strip dock like the company tab.
 */
import type { ArticleRow, ServerClient, WirePayload } from '../adapters/serverClient';
import type { RoundResponse } from '../adapters/serverClient';

export interface Newsroom {
  destroy(): void;
}

function escape(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function renderArticle(article: ArticleRow): string {
  const subjects = article.tickers.length > 0 ? article.tickers.join(', ') : 'Market';
  return `<article class="vi-nr-item">
    <h3>${escape(article.headline)}</h3>
    <div class="vi-news-meta">${escape(article.kind)} · ${escape(subjects)}</div>
    <p class="vi-co-desc">${escape(article.body)}</p>
  </article>`;
}

export function mountNewsroom(
  strip: HTMLElement,
  client: ServerClient,
  round: RoundResponse,
): Newsroom | null {
  // Same dock-join as the company tab: the strip lives inside the positions
  // view inside the dock once mountPortfolio has wrapped it.
  const dock = strip.parentElement?.parentElement;
  const tabs = dock?.querySelector('.vi-tabs');
  if (dock == null || tabs == null) {
    return null;
  }

  const button = document.createElement('button');
  button.setAttribute('data-tab', 'newsroom');
  button.textContent = 'Newsroom';
  tabs.appendChild(button);

  const view = document.createElement('div');
  view.className = 'vi-tab';
  view.hidden = true;
  view.innerHTML = `<div class="vi-nr-list"><p class="vi-empty">No stories yet — headlines appear here once the desk writes them.</p></div>`;
  dock.appendChild(view);
  const list = view.querySelector('.vi-nr-list') as HTMLElement;

  let articles: ArticleRow[] = [];
  const render = (): void => {
    list.innerHTML = articles.length === 0
      ? `<p class="vi-empty">No stories yet — headlines appear here once the desk writes them.</p>`
      : articles.map(renderArticle).join('');
  };
  const upsert = (article: ArticleRow): void => {
    articles = [article, ...articles.filter((entry) => entry.newsId !== article.newsId)];
    render();
  };

  const reload = async (): Promise<void> => {
    try {
      const rows = await client.articles(round.roundId);
      articles = [...rows].reverse();
      render();
    } catch {
      // Keep whatever live payloads already arrived; retry on reconnect.
    }
  };
  void reload();

  const onTabs = (event: Event): void => {
    const target = event.target as HTMLElement;
    if (target.getAttribute('data-tab') === null) {
      return;
    }
    tabs.querySelectorAll('button').forEach((b) => b.classList.toggle('vi-active', b === target));
    view.hidden = target.getAttribute('data-tab') !== 'newsroom';
  };
  tabs.addEventListener('click', onTabs);

  const unsubscribe = client.onPayload((payload: WirePayload) => {
    if (payload.type !== 'news' || typeof payload.body !== 'string' || payload.body.length <= 80) {
      return;
    }
    const tickers = typeof payload.symbol === 'string' && payload.symbol !== '' ? [payload.symbol] : [];
    upsert({
      newsId: String(payload.newsId),
      roundId: round.roundId,
      tick: Number(payload.tick) || 0,
      kind: String(payload.scope ?? 'MARKET'),
      tickers,
      headline: String(payload.headline),
      body: payload.body,
      model: 'wire',
    });
  });
  const stopReconnect = client.onReconnect(() => {
    void reload();
  });

  return {
    destroy() {
      tabs.removeEventListener('click', onTabs);
      unsubscribe();
      stopReconnect();
      button.remove();
      view.remove();
    },
  };
}
