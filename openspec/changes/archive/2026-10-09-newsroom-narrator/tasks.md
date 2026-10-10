# Tasks

## 1. Measure and persist

- [x] 1.1 Measure news-release volume per round (counts by scope and kind on REALISTIC 30m) and verify the selection threshold + per-round cap against a 10–25s/article budget
- [x] 1.2 Add the `articles` table (`round_id, news_id, tick, kind, tickers, headline, body, model, created_at`) with a persistence test, and verify round-trip storage of a template article
- [x] 1.3 Add `GET /api/rounds/{id}/articles` (under `X-API-Version`) and verify a late joiner receives the full archive oldest-first

## 2. Narrator module

- [x] 2.1 Create `server/narrator` with the `Narrator` interface plus `TemplateNarrator` and `DisabledNarrator`, wire `vi.narrator.*` config defaulting to templates, and verify today's headlines still publish with the module on
- [x] 2.2 Add `LlamaCppNarrator` (sidecar `/completion` client with timeout, prompt pinned to the fictional universe, denylist post-filter that drops on hit) and verify a dropped article leaves its headline standing alone
- [x] 2.3 Subscribe the narrator at the edge projection: headlines publish instantly with their generator note, completed articles re-broadcast under the same `newsId` (body presence is the marker), and verify replay determinism is untouched (same seed, same stream)

## 3. Client and docs

- [x] 3.1 Upsert the web news feed by `newsId` and verify an article body arriving late updates its headline in place without duplicates
- [x] 3.2 Build the newsroom tab (history from the endpoint, live updates from the feed) following the company-tab mounting pattern, and verify on desktop and phone widths with a screenshot
- [x] 3.3 Document sidecar setup (llama.cpp server mode, ~1B Q4, opt-in), the degradation ladder, and the preview/recap kinds in `docs/`, and verify a fresh operator can bring the narrator up from the doc alone
- [x] 3.4 Run the full verification (`mvn test` per repo guide, `cd web && npm run build`, `openspec validate --specs`) and verify everything is green

## 4. Docker sidecar (integral, not manual)

- [x] 4.1 Add the `narrator` compose profile (llama.cpp server image, weights volume, localhost-only port, healthcheck) with `fetch-model.sh` + entrypoint, and verify `docker compose config` is valid
- [x] 4.2 Download the preconfigured tiny weights (Qwen2.5-0.5B-Instruct Q8, ~640 MB) at install, start the sidecar, and verify a real `/completion` round-trip
- [x] 4.3 Run the edge in llama mode against the docker sidecar and verify a genuine model-labeled article lands end to end (store → wire → tab)
