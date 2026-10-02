# Design

## Context

See `proposal.md` for motivation and `specs/*/spec.md` for requirements. This is a greenfield project: the repository holds only OpenSpec scaffolding. The design must therefore establish the whole technical spine, and it must respect two facts discovered while planning:

- The terminal already exists and is not ours to rebuild. `openalgo-charts` (cloned at `/home/velteyn/projects/openalgo-charts`) is a ~108k-LOC TypeScript canvas charting/trading library exposed as nine loadable tiers. Critically, it is broker- and data-agnostic: its `DataFeed` (bars, depth, quotes, symbol search), `OrderFeed`/`AccountFeed` (place/modify/cancel, order stream, accounts), and `NewsFeed` interfaces are the seam where our market plugs in. Its `examples/yfinance` host proves the composition.
- The machine is modest (i5-5257U-class CPU, Java 21, Maven, Docker Engine with Compose v5 already running other containers). The stack must stay lean and single-node.

A prior reference for matching logic exists locally in `webcurvesim` (Java exchange simulator with price-time priority order books and FIX). It is a source of ideas, not a dependency.

The user has confirmed: real-time sessions (30 min default, configurable), pre-seeded market history, visible named rival bots, institutions as the market, pure intraday scope, on-demand sessions (persistent world deferred), and a save/resume capability backed by a database.

## Goals / Non-Goals

**Goals:**

- A deterministic, headless market core that runs, and is fully testable, with no server or framework.
- A server that is authoritative over all state and safe to restart mid-session.
- A terminal experience that reuses `openalgo-charts` unchanged, adapted only by our feed implementations.
- A single-node Docker Compose stack that a developer can run and demo locally.
- Extensibility toward a persistent always-on world without redesign.

**Non-Goals:**

- No options/futures/CFDs, M&A, or player-authored bots in this change.
- No cloud/cluster concerns (horizontal scaling, HA) now.
- No mobile-native client; browser only.
- No real market data ingestion; the universe is synthetic and self-contained.

## Decisions

### D1. Java 21 + Spring Boot server, TypeScript terminal

**Decision:** The world and game server are Java 21 / Spring Boot; the terminal is a TypeScript browser app built on `openalgo-charts`.

**Rationale:** Multi-player concurrency, deterministic simulation control, mature persistence/WebSocket/security, and the user's stated preference point to the JVM. Java 21 + Maven are already installed.

**Alternatives considered:** All-TypeScript (shares types with the terminal, faster solo iteration, but weaker for long-lived concurrent simulation); reusing the Java `webcurvesim` engine directly (deeper coupling, older stack). Chosen against; the wire contract will bridge the language boundary once.

### D2. The deterministic core is framework-free

**Decision:** Matching, books, fundamentals, bots, and the world clock live in a plain-Java module (`core`) with no Spring, no JPA, no network, no wall-clock, and no unseeded randomness. Spring wraps it as a single managed component on the edge.

**Rationale:** This is what makes determinism, replay, fast unit tests, and the "core runs from a `main()`" property true. It also keeps the hot path out of proxy/transaction overhead.

**Alternatives considered:** Spring-managed tick loop (rejected: proxies and AOP on the hot path, harder to test deterministically); separate simulation microservice process (deferred: extra ops for no current benefit, but the module boundary keeps it possible).

### D3. Fixed-step world clock with seeded randomness

**Decision:** The world advances in fixed steps (default 100 ms of wall clock, tunable) driven by a single scheduler. Each subsystem draws from its own seeded PRNG stream derived from the round seed. Human input is stamped with the receiving tick and applied at the next step with a modeled latency of one to two ticks.

**Rationale:** Fixed steps make the simulation a pure function of (seed, event sequence). Seeded per-subsystem streams prevent one bot's draws from perturbing another's, which naive shared `Random` would.

**Alternatives considered:** Free-running wall-clock event loop (jitter, non-reproducible); continuous-time market (harder to reconcile with discrete human input). A 100 ms step is chosen over 10 ms to bound CPU on the target machine while staying imperceptible; depth broadcasts are coalesced to ~250 ms.

### D4. Event sourcing with periodic snapshots

**Decision:** Every world-affecting action is appended to an ordered event log; the log plus the round seed is the source of truth. Compact full-state snapshots are written every N events or ticks (default ~10 s). Read models (leaderboard, portfolios, history) are projections built from the log.

**Rationale:** Directly satisfies `persistence-resume` (reconnect, restart recovery, pause/resume) and gives replay, audit, and spectating nearly for free. It also enforces the determinism rule: if it is not in the log, it cannot have happened.

**Alternatives considered:** Periodic full-state upsert only (simpler, but loses intra-interval history and any replay/audit); per-tick DB writes (rejected outright).

### D5. Hybrid price formation

**Decision:** Each instrument has a latent fundamental value `F` driven by a market factor, a sector factor, idiosyncratic diffusion, and discrete jumps. Market makers quote around `F` with spreads scaled by volatility and inventory; the traded price `P` emerges from order-book matching. Value-oriented bots trade `P` toward `F`; momentum bots push `P` away from `F`; news moves `F` and the volatility state.

**Rationale:** Gives believable, self-contained price paths with realistic stylized facts (fat tails from jumps, volatility clustering from a vol state, cross-sectional correlation from factors) while letting the player's own size move `P` and detach it from `F`.

**Alternatives considered:** Pure replay of generated history (prices ignore the player); pure agent-based with no anchor (hard to calibrate, drifts unboundedly). The hybrid needs both halves and is the plan.

### D6. One participant abstraction for humans, bots, and institutions

**Decision:** The engine knows only `Participant` (account, buying power, order entry, fills). Human players, the five named rivals, and institutions are all participants; humans arrive over WebSocket, bots run in-process. Strategy, identity, and leaderboard visibility are attributes, not types.

**Rationale:** Makes "a human player can later join" a config change rather than an architecture change, and lets bots be tested through the exact same API the server exposes.

### D7. Versioned wire protocol: WebSocket for streaming, REST for the rest

**Decision:** A single WebSocket carries market data (L1/L2, trades), order/account events, and news. REST handles symbol search, round creation/configuration, leaderboard queries, history, and auth. JSON first; the schema is versioned and generated so a protobuf upgrade is possible without a redesign.

**Rationale:** Matches `openalgo-charts`' feed model (streaming vs request/response) and keeps the contract explicit across the Java/TS boundary.

### D8. Terminal composed as an `openalgo-charts` host

**Decision:** The frontend is a thin TS app (Vite, framework-light, modeled on the `yfinance` reference host) that instantiates the `openalgo-charts` widget and supplies four adapters: `DataFeed` (bars/depth/search → REST+WS), `OrderFeed`/`AccountFeed` (orders/positions/accounts → WS+REST), `NewsFeed` (wire → WS), and a symbol/search adapter. No chart code is forked.

**Rationale:** Reuses the entire terminal (charts, indicators, drawings, DOM ladder, order ticket, brackets, replay, watchlist, workspaces) and confines our work to adapters, which is the highest-leverage boundary available.

**Alternatives considered:** Fork/extend `OpenCharts` (a complete app, but its paper engine is replay-based and would be replaced anyway); build a bespoke UI (rejected: months of charting work for no product gain).

### D9. Persistence on PostgreSQL via Docker Compose; Redis deferred

**Decision:** Postgres holds the event log, snapshots, and read models. Compose defines `db`, `api`, and `web`; the database runs permanently during development while `api`/`web` run natively or containerized. Redis is added only if the leaderboard or presence fan-out needs it.

**Rationale:** One durable, transactional store is sufficient; JSONB covers both the event payloads and snapshot blobs. Keeps the single-node footprint small.

### D10. Module layout

```
  server/ (Maven)
    core/        framework-free: clock, books, matching, fundamentals,
                 instruments, market rules, participants, bots, news model
    game/        round lifecycle, launch pad config, ranking, settlement
    persist/     event log, snapshots, projections, JPA/Flyway
    edge/        Spring Boot: REST, WebSocket, auth, DTO mapping, wire protocol
  web/ (Vite + TS)
    adapters/    DataFeed, OrderFeed/AccountFeed, NewsFeed, symbol search
    app/         launch pad, terminal mount, routing
  infra/
    docker-compose.yml, Postgres init, Flyway migrations
```

**Rationale:** Mirrors decisions D2/D4 and keeps core dependency-free and independently runnable.

### D11. Session/round lifecycle and launch pad

**Decision:** A round is the unit of state: `seed + config` produce a warm-up phase (generate history and pre-seed the book), then a running session (opening auction → continuous → closing auction), then settlement. The launch pad collects starting capital, duration, and realism preset; rivals are seeded with the same capital (spec: fair ranking).

**Rationale:** A round that is fully described by `seed + config + events` is exactly what save/resume and replay need.

### D12. Bot architecture

**Decision:** Each bot is a stateless-per-tick `Strategy` that observes market data and its own account and emits orders through the participant API; state that matters is held on the participant/account, not in the strategy. Rivals use distinct strategies (market maker, momentum, value, scalper, news-hunter); institutions use VWAP-execution and macro-reaction strategies and are excluded from the leaderboard.

**Rationale:** Keeps bots deterministic and testable, and keeps "react to the market" as data-driven rather than hardcoded.

### D13. Event/command taxonomy

```
  COMMANDS (from clients/bots)        EVENTS (appended by the world)
  ----------------------------        -----------------------------
  PlaceOrder, ModifyOrder,            OrderAccepted, OrderRejected,
  CancelOrder, SetArmed               OrderRested, OrderTriggered,
                                      OrderPartiallyFilled, OrderFilled,
                                      OrderCancelled
  CreateRound, PauseRound,            RoundCreated, WarmupComplete,
  ResumeRound, JoinRound,             AuctionOpened, AuctionClosed,
  Reconnect                           SessionOpened, SessionClosed,
                                      RoundPaused, RoundResumed, RoundSettled
                                      NewsReleased, NewsConfirmed,
                                      NewsDenied
  (system) Tick                       MarketFactorMoved, FundamentalJumped,
                                      VolatilityStateChanged, BookChanged,
                                      TradePrinted, BarClosed,
                                      MarginCallIssued, LiquidationExecuted
```

**Rationale:** Separating intent (commands) from fact (events) is the event-sourcing discipline; replaying events alone reproduces the world, while commands explain why.

## Risks / Trade-offs

- **Determinism drift** → Any unseeded `Random`, wall-clock read, or unordered map iteration in `core` breaks replay. Mitigation: forbid wall-clock and default `Random` in `core` (lint/architecture test), seed per subsystem, and add a golden-master test that replays a fixed round and compares full state.
- **CPU on a modest machine** → Java + Postgres + a 10 Hz world + bots can contend. Mitigation: 100 ms step, coalesced depth broadcasts, JVM `-Xmx512m`, no Redis initially, and a benchmark bot count cap.
- **Language boundary** → Java ↔ TS type drift. Mitigation: a single schema artifact for the wire protocol, generated DTOs, and contract tests on both sides.
- **`openalgo-charts` mismatches** → Feed semantics (e.g., depth level counts, bar intervals, account shapes) may not map cleanly. Mitigation: adopt its reference-host patterns and build adapters against its `examples/yfinance` proof; validate with its own test harness.
- **Calibration** → Hybrid parameters that produce lifeless or absurd markets. Mitigation: an offline calibration harness that generates rounds and checks stylized facts (return distribution, autocorrelation, spread) before wiring the UI.
- **Scope creep** → Persistent world, options, M&A tempt early. Mitigation: explicit non-goals in the proposal; the core's on-demand/always-on switch is designed but unimplemented.

## Migration Plan

Greenfield, so "migration" is bootstrap and safe evolution:

1. Scaffold `server/` with `core` as the first module and a runnable determinism test; no Spring required to prove it.
2. Add `edge` REST to expose the instrument master and create a round; verify with `curl`.
3. Add the WebSocket stream and a headless bot-only round; verify prices move and books update from logs.
4. Stand up `web/` adapters against the running server and mount the terminal; verify the player can trade a bot-only market.
5. Add `persist` (log, snapshots, projections) and prove restart recovery and pause/resume.
6. Add the news engine and the five rivals; run a full 30-minute session end to end.

Rollback: each step is additive; failing a step is reverted by removing that module/service without affecting earlier ones. DB schema changes are additive Flyway migrations, never destructive.

## Open Questions

- Exact realism-preset definitions (which rules toggle per preset) — safe to finalize during implementation once the rule set exists.
- Whether the closing auction force-closes only margin positions or all positions — the spec allows either; decide during settlement implementation.
- Depth level count to broadcast (5 vs 10 vs 20) — decide by measuring openalgo-charts ladder rendering and bandwidth.
- Whether to persist per-tick `BookChanged` events or only deltas — decide with a storage/benchmark spike; the log schema allows either.
