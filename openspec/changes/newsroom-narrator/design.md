# Design

## Context

See proposal.md (Why). Current state, verified in code:

- Releases are domain events (`GameEvent.NewsReleased` with magnitude, symbol, sector, scope, tick, duration) folded by `RunningRound.routeReleases` into bot `NewsSignal`s on the deterministic path.
- The edge projects releases to `WireMessage.NewsDto`, which already carries a `body` field — always empty today. The web `VirtualInvestorNewsFeed` already maps `payload.body` to its item `summary`. Articles can ride the existing wire with zero protocol change.
- Server modules are `core`, `edge`, `game`, `persist`, `runtime`. `persist` has `event/`, `session/`, `player/`, `projection/`, `snapshot/` packages backed by Postgres (`infra/docker-compose.yml` runs only the DB).
- `company-profiles` is archived: 21 fictional profiles exist as content, and the company-tab mounting pattern is the template for the newsroom tab.
- Machine reality from exploration: i5/8GB → llama.cpp server mode with a ~1B Q4 model at roughly 10–30 tok/s; a 150-word article costs ~10–25s.

## Goals / Non-Goals

**Goals:**

- Headlines gain bodies: earnings, sector, and macro releases become short grounded stories; round start/end gain preview/recap markers.
- Late joiners read the session's story from stored articles.
- Everything degrades to today's bare headlines with the sidecar off.

**Non-Goals:**

- No strategist: articles never advise, never rank trades, never address the player.
- No bot consumption: `NewsSignal`s stay the only machine-readable news; articles are content for humans.
- No determinism coupling: article timing, wording, and even existence must not change replays, seeds, or the calibration gates.

## Decisions

**1. Tap the edge projection, not the simulation (over hooking `NewsGenerator` or `RunningRound`).**

The deterministic path (`NewsGenerator` → `routeReleases` → bots) stays byte-identical. The narrator subscribes where `NewsReleased` becomes `NewsDto`: headlines publish instantly with empty bodies, and a completed article re-broadcasts the same `newsId` with `body` filled and `stage: "article"`. The web feed upserts by `newsId` (tiny adapter change) so the wire stays a single stream.

**2. New `server/narrator` module behind a `Narrator` interface (over a package inside `runtime`).**

`LlamaCppNarrator` (HTTP `/completion` against the sidecar), `TemplateNarrator` (headline + profile sentences, no model), `DisabledNarrator` (no-op). Selected by `vi.narrator.*` config (enabled, url, timeout, per-round cap); default is template-on/sidecar-off so a stock checkout behaves exactly as today plus template bodies. Physical separation is the decoupling contract made obvious.

**3. Selection policy with a measured volume cap (over one article per release).**

Release volume per round is currently unmeasured against a 10–25s/article budget, so the first task measures it. Policy from that number: earnings + sector + macro always eligible; low-magnitude randoms below a threshold skipped; hard per-round cap with skip-not-queue on overflow (prose never backpressures the market). Preview/recap are reserved kinds outside the cap.

**4. Fictional universe by prompt + post-filter (over review, which is impossible at runtime).**

The system prompt pins the 21 fictional names and forbids real companies, outlets, and advice. Every body passes the profile denylist before broadcast; on a hit the article is dropped and the headline stands alone. No player input ever enters a prompt — only simulation facts — so there is no injection surface beyond the model itself.

**5. `articles` table + history endpoint (over stuffing history into resync).**

`articles(round_id, news_id, tick, kind, tickers, headline, body, model, created_at)`; `GET /api/rounds/{id}/articles` serves late joiners and the newsroom tab's initial page, live payloads keep it current. Resync stays simulation-only.

## Risks / Trade-offs

- [Sidecar throughput starves the story (releases outrun 10–25s/article)] → Mitigation: measure first (task), then threshold + cap; worst case the round reads as today plus templates.
- [A second payload per article doubles news traffic] → Mitigation: bodies are short (cap ~150 words), and only selected releases get them; accepted.
- [Two article sources (live payloads vs endpoint) diverge] → Mitigation: `newsId` is the single key on both; upsert semantics everywhere.
- [Nondeterministic prose complicates bug reports] → Mitigation: stored rows carry `model` + timestamp; replays ignore articles entirely.

## Migration Plan

Disabled/template by default — every step ships dark. Order: measure release volume → persist + endpoint → narrator module with template first → wire fill + client upsert → sidecar client + prompt/filter → newsroom tab → docs. Rollback at any point is `vi.narrator.enabled=false`. Figure-like flow: `NewsReleased` → edge projection (headline now) → narrator queue (article later) → same-`newsId` payload + articles row → feed upsert + newsroom tab.
