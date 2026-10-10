/**
 * `NewsFeed` adapter backed by the server's `news` frames. The library's feed
 * is request/response (a page of news), while the wire is a push stream, so
 * this adapter keeps a rolling buffer: arriving headlines are accumulated and
 * `getNews` returns the newest page from it. Articles arrive as second payloads
 * under the same news id (newsroom-narrator 2.3) and upsert the headline in
 * place instead of duplicating it.
 */
import type { NewsFeed, NewsItem, NewsPage, NewsRequest } from 'openalgo-charts';
import type { ServerClient, WirePayload } from './serverClient';

const MAX_BUFFER = 200;

export class VirtualInvestorNewsFeed implements NewsFeed {
  private readonly buffer: NewsItem[] = [];
  private readonly unsubscribe: () => void;

  constructor(client: ServerClient) {
    this.unsubscribe = client.onPayload((payload: WirePayload) => {
      if (payload.type !== 'news') {
        return;
      }
      const item: NewsItem = {
        id: String(payload.newsId),
        headline: String(payload.headline),
        source: String(payload.source),
        time: Number(payload.tick ?? Date.now() / 1000),
        summary: typeof payload.body === 'string' ? payload.body : undefined,
      };
      const existing = this.buffer.findIndex((entry) => entry.id === item.id);
      if (existing >= 0) {
        // Same-newsId re-broadcast (article body arrived): refresh in place and
        // treat as newest so the story surfaces.
        this.buffer.splice(existing, 1);
      }
      this.buffer.unshift(item);
      if (this.buffer.length > MAX_BUFFER) {
        this.buffer.length = MAX_BUFFER;
      }
    });
  }

  async getNews(request: NewsRequest): Promise<NewsPage> {
    const limit = request.limit > 0 ? request.limit : 20;
    const symbol = request.symbol;
    const items = this.buffer.filter((item) => symbol === undefined || item.headline.includes(symbol));
    return { items: items.slice(0, limit), nextCursor: null };
  }

  dispose(): void {
    this.unsubscribe();
  }
}
