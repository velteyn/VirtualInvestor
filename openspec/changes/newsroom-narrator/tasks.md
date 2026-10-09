# Tasks

## 1. Measure and persist

- [ ] 1.1 Measure news-release volume per round (counts by scope and kind on REALISTIC 30m) and verify the selection threshold + per-round cap against a 10–25s/article budget
- [ ] 1.2 Add the `articles` table (`round_id, news_id, tick, kind, tickers, headline, body, model, created_at`) with a persistence test, and verify round-trip storage of a template article
- [ ] 1.3 Add `GET /api/rounds/{id}/articles` (under `X-API-Version`) and verify a late joiner receives the full archive oldest-first

## 2. Narrator module

- [ ] 2.1 Create `server/narrator` with the `Narrator` interface plus `TemplateNarrator` and `DisabledNarrator`, wire `vi.narrator.*` config defaulting to templates, and verify today's headlines still publish with the module on
- [ ] 2.2 Add `LlamaCppNarrator` (sidecar `/completion` client with timeout, prompt pinned to the fictional universe, denylist post-filter that drops on hit) and verify a dropped article leaves its headline standing alone
- [ ] 2.3 Subscribe the narrator at the edge projection: headlines publish instantly with empty bodies, completed articles re-broadcast the same `newsId` with `stage: "article"`, and verify replay determinism is untouched (same seed, same stream)

## 3. Client and docs

- [ ] 3.1 Upsert the web news feed by `newsId` and verify an article body arriving late updates its headline in place without duplicates
- [ ] 3.2 Build the newsroom tab (history from the endpoint, live updates from the feed) following the company-tab mounting pattern, and verify on desktop and phone widths with a screenshot
- [ ] 3.3 Document sidecar setup (llama.cpp server mode, ~1B Q4, opt-in), the degradation ladder, and the preview/recap kinds in `docs/`, and verify a fresh operator can bring the narrator up from the doc alone
- [ ] 3.4 Run the full verification (`mvn test` per repo guide, `cd web && npm run build`, `openspec validate --specs`) and verify everything is green
