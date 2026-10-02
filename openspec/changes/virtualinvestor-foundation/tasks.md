# Tasks

## 1. Project scaffolding

- [x] 1.1 Create the Maven multi-module layout `server/core`, `server/game`, `server/persist`, `server/edge` with a parent POM; verify `mvn -q -pl server/core test` compiles an empty module.
- [x] 1.2 Create the TypeScript app under `web/` with Vite and add `openalgo-charts` as a dependency; verify `npm install` and `npm run build` succeed.
- [x] 1.3 Add `infra/docker-compose.yml` with a Postgres 16 service, named volume, and healthcheck; verify `docker compose up -d db` reports healthy.
- [x] 1.4 Write `README.md` with the module map, prerequisites (Java 21, Maven, Node, Docker), and the dev commands; verify the documented commands run as written.

## 2. Deterministic core foundation

- [x] 2.1 Implement the fixed-step world clock (default 100 ms) with a start/stop/pause API and an injectable time source; verify a unit test advances N steps deterministically.
- [x] 2.2 Implement seeded per-subsystem PRNG streams derived from the round seed; verify two instances with the same seed produce identical sequences across subsystems.
- [x] 2.3 Define the command and event types from design D13 as immutable records; verify a serialization round-trip test for every type.
- [x] 2.4 Add an architecture test that rejects wall-clock calls, default `Random`, and unordered-map iteration inside `core`; verify the test passes on the clean module and fails on a deliberate violation.
- [x] 2.5 Build a golden-master harness that runs a fixed-seed scenario and hashes full world state; verify a re-run produces the same hash.

## 3. Instrument universe and fundamentals

- [x] 3.1 Define the `Instrument` model (ticker, name, sector, shares, float, tick, quantity grid, reference price, volatility, factor exposures) and load a fictional universe of ~20 names plus one index from a data file; verify a loader test asserts no real-company names and unique tickers.
- [x] 3.2 Implement the market and sector factor processes plus per-instrument idiosyncratic diffusion; verify a test shows correlated moves for high-exposure names and near-independence for low.
- [x] 3.3 Implement discrete fundamental jumps sourced from news events; verify a jump test changes only the targeted scope.
- [x] 3.4 Add symbol search over ticker and name; verify a search test returns expected matches and metadata.
- [x] 3.5 Add a calibration harness that generates rounds and reports return distribution, autocorrelation, and cross-correlation; verify it runs and documents current stylized facts in `docs/calibration.md`.

## 4. Order book and matching engine

- [x] 4.1 Implement the price-time-priority order book with add/modify/cancel and level aggregation; verify unit tests for priority ordering and correct aggregated depth.
- [x] 4.2 Implement MARKET and LIMIT matching with partial fills; verify tests for full fill, partial fill with remainder resting, and rejection per type.
- [x] 4.3 Implement STOP and STOP-LIMIT triggers driven by last traded price; verify tests for trigger conversion on cross and non-trigger when untraded.
- [x] 4.4 Emit order lifecycle and trade-print events from the matching engine; verify an event-sequence test for a scripted set of orders.
- [x] 4.5 Benchmark matching throughput per instrument; verify the benchmark runs and its numbers are recorded in `docs/calibration.md`.

## 5. Market rules, sessions, and auctions

- [x] 5.1 Enforce tick-size rounding, quantity grid, and minimum quantity with explicit rejection reasons; verify tests for each rejection.
- [x] 5.2 Implement short-sale availability and price bands/circuit breakers with halt and resume; verify tests for a halted instrument refusing orders and resuming after the period.
- [x] 5.3 Implement the intraday session state machine (pre-open, opening auction, continuous, closing auction) over the world clock; verify a timeline test asserts phase transitions at the right ticks.
- [x] 5.4 Implement opening and closing auctions that compute the volume-maximizing clearing price; verify tests for a known book producing the expected open and close.
- [x] 5.5 Document the market rules and their per-preset toggles in `docs/market-rules.md`; verify the document matches the implemented rule set.

## 6. Participants, bots, and institutions

- [x] 6.1 Define the `Participant` abstraction (account, buying power, order entry, fills) and refactor the engine to accept all traders through it; verify a test where a scripted in-process participant trades identically to a simulated human command.
- [x] 6.2 Implement account and position accounting with mark-to-market equity; verify tests for average price, realized/unrealized P&L, and short positions.
- [ ] 6.3 Implement the market-maker strategy quoting around fundamentals with inventory skew; verify a test that spreads widen as volatility rises and inventory skews quotes.
- [ ] 6.4 Implement momentum, value, scalper, and news-hunter rival strategies; verify a scripted-news test where each rival reacts in its declared direction.
- [ ] 6.5 Implement institution strategies (VWAP execution and macro reaction) and exclude them from ranking; verify a test that institution fills move price and that institutions are absent from the leaderboard projection.
- [ ] 6.6 Document the bot roster, their identities, and strategies in `docs/bots.md`; verify the document lists all five rivals and the institutions.

## 7. News engine

- [ ] 7.1 Define the news model (source, headline, body, scope, direction, magnitude, duration) with fictional sources; verify a validation test rejects real-source names.
- [ ] 7.2 Implement scheduled and random event generation seeded per round, with cadence scaled to session length; verify a same-seed test produces identical event times.
- [ ] 7.3 Implement rumor-then-confirm/deny staging with market reaction at rumor time; verify a test showing a price move at rumor and a further move at confirmation/denial.
- [ ] 7.4 Wire news impact to fundamentals and the volatility state; verify a test showing higher realized volatility and wider spreads after a significant event.
- [ ] 7.5 Document the taxonomy and impact model in `docs/news.md`; verify the document matches the implemented event set.

## 8. Game layer: rounds, ranking, settlement

- [ ] 8.1 Implement the launch pad configuration model (capital, duration, realism preset) with range validation; verify tests for accepted and rejected configurations.
- [ ] 8.2 Implement round lifecycle states (created, warm-up, running, closing, settled) with `seed + config` identity; verify a lifecycle test asserts allowed transitions only.
- [ ] 8.3 Implement the warm-up phase that generates history and pre-seeds books before trading; verify a test that bars exist and the book is non-empty on session open.
- [ ] 8.4 Implement ranking by final equity with return-on-capital as a secondary figure, seeding rivals with the player's capital; verify a test that unequal capital inputs still rank rivals fairly.
- [ ] 8.5 Implement margin call detection, liquidation, and settlement (mark to close, force-close margin positions, finalize ranking); verify tests for a mid-session liquidation and an end-of-session settlement.
- [ ] 8.6 Document the round lifecycle and settlement rules in `docs/game-session.md`; verify the document matches the implemented states and rules.

## 9. Persistence, recovery, and resume

- [ ] 9.1 Add Flyway migrations for `events`, `snapshots`, `rounds`, `participants`, `accounts`, `positions`, `orders`, `fills`, and `results`; verify migrations apply cleanly to an empty database.
- [ ] 9.2 Implement the append-only event log writer with monotonic sequence and tick, batched off the hot path; verify a test asserting ordering and that no existing row is ever updated or deleted.
- [ ] 9.3 Implement periodic snapshot writing and a loader that rebuilds state from snapshot plus subsequent events; verify the golden-master hash after rebuild equals the live hash.
- [ ] 9.4 Implement read-model projections (leaderboard, portfolio, trade history, results) rebuildable from the log; verify a test that rebuilds projections from a log and matches live values.
- [ ] 9.5 Implement player reconnect with session token and full resync of orders, positions, account, and market snapshot; verify a test that reconnects and matches state.
- [ ] 9.6 Implement server-restart recovery (load snapshot, replay, resume clock) and deliberate pause/resume; verify a test that stops and restarts mid-round with no lost or duplicated fills.
- [ ] 9.7 Document the persistence model, snapshot cadence, and recovery procedures in `docs/persistence.md`; verify the documented recovery steps work in a local run.

## 10. Server edge and wire protocol

- [ ] 10.1 Define the versioned wire schema for market data, orders, accounts, news, and round control; verify a contract test serializes every message type.
- [ ] 10.2 Implement REST endpoints for symbol search, round create/configure, leaderboard, history, and auth; verify an integration test using an HTTP client.
- [ ] 10.3 Implement the WebSocket gateway streaming L1/L2, trades, order/account events, and news with coalesced depth updates; verify a test client receives each stream type.
- [ ] 10.4 Map engine commands/events to wire messages at the edge without leaking `core` types; verify an architecture test that `edge` depends on `core` and not the reverse.
- [ ] 10.5 Document the wire protocol in `docs/wire-protocol.md`; verify the document matches the generated schema.

## 11. Terminal adapters and launch pad UI

- [ ] 11.1 Implement the `DataFeed` adapter (bars, depth, symbol search) against REST+WS; verify against a running server with the `openalgo-charts` reference-host patterns.
- [ ] 11.2 Implement the `OrderFeed`/`AccountFeed` adapter (place/modify/cancel, streams, accounts) and map server rejects to engine states; verify a test that places, modifies, cancels, and reconciles a position.
- [ ] 11.3 Implement the `NewsFeed` adapter and wire it to the terminal news panel; verify headlines appear in the panel as events fire.
- [ ] 11.4 Build the launch pad screen (capital, duration, preset) that creates a round and opens the terminal; verify the documented manual flow starts a session.
- [ ] 11.5 Mount the `openalgo-charts` widget with our adapters and confirm chart, DOM ladder, order ticket, positions, and leaderboard render live data; verify via the app's manual run and a smoke test.
- [ ] 11.6 Document the terminal setup and adapter contract in `docs/terminal.md`; verify the documented dev commands reproduce the terminal.

## 12. Integration and end-to-end checks

- [ ] 12.1 Run a full 30-minute bot-only round via the backend and verify prices move, books update, news fires, and the round settles with a finalized leaderboard.
- [ ] 12.2 Run a full session with a human player trading against the five rivals through the terminal and verify orders, fills, P&L, settlement, and ranking behave correctly.
- [ ] 12.3 Verify restart recovery end to end: stop the server mid-session, restart, and confirm the world resumes to the same state.
- [ ] 12.4 Verify pause/resume end to end: pause a session, resume later, and confirm accounts, positions, pending orders, and clock continue exactly.
- [ ] 12.5 Perform a full-stack resource check on the target machine (Java, Postgres, terminal) and record observed CPU and memory in `docs/calibration.md`.
