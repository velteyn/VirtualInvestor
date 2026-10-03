# Server runtime

The server can run a live round: a deterministic world loop that streams to clients and accepts
orders. This is the implementation behind the `live-market-server` change.

## Modules

```
core      framework-free market simulation (clock, books, matching, fundamentals, news, bots)
game      rounds, launch config, ranking, settlement, warm-up
persist   event log, snapshots, recovery, projections, sessions
runtime   RunningRound: composes core + persist into a running world  <-- new
edge      Spring Boot: REST + WebSocket, round control, streaming
```

The `runtime` module has no Spring dependency; the `edge` starts and drives it.

## The world loop

`com.virtualinvestor.runtime.RunningRound` owns one round. `step()` advances exactly one tick
in a fixed, documented order:

1. **Apply queued client commands** (drained from the single-producer `CommandQueue`).
2. **Advance fundamentals and volatility** (`FundamentalModel`, `VolatilityState`).
3. **Advance the session phase**, running opening/closing call auctions at the boundaries.
4. **Release due news** (`NewsEngine`).
5. **Let the bots act** (only in `CONTINUOUS`): market makers re-quote, rivals take.
6. **Enforce margin** (`MarginManager`) and publish the tick's events.

The loop is single-writer and deterministic: the same seed and event log reproduce the same
state. Wall-clock time is used only by the real-time driver, never inside the simulation.

## Starting a round

`RoundRuntimeService` (edge) creates and starts a round:

- `start(roundId, seed, config, runInRealTime)` warms up (history + seeded books) and starts a
  `RealTimeDriver` stepping the clock every `stepMillis` (default 100 ms).
- `pause`/`resume` map to lifecycle + driver control.
- `submit` maps a wire order command to a core `Command` and enqueues it on the round.

REST: `POST /api/rounds` (create + start), `POST /api/rounds/control` (pause/resume),
`POST /api/rounds/leaderboard`. All versioned with the `X-API-Version` header.

## Order intake

Client commands arrive as wire payloads (`placeOrder`, `modifyOrder`, `cancelOrder`), are mapped
to core commands, and applied through `TradeGateway`:

- pre-trade validation (tick grid, quantity grid/minimum, short-sale, halts) with explicit reasons;
- idempotency by client token (a retried command is applied once);
- every fill is applied to **both** counterparties' accounts before it is published.

## Streaming

`RoundBroadcaster` (edge) implements the runtime's `EventPublisher`: it maps core events to wire
payloads (trade prints, news, order status) and publishes a coalesced depth snapshot per symbol
through `MarketBroadcaster` over `/ws`. Each streamed event carries its sequence and tick; a
reconnecting client sends `resync` and receives the `resyncResult` of events it missed.

## Persistence and recovery

`JournaledWorldJournal` appends the loop's events through `BatchedEventLogWriter` and writes a
snapshot every N ticks via `RoundRecoveryService`. On startup, `RunningRound.recover(tick,
paused)` rebuilds by replaying the persisted history to the recovered tick and resumes running
or paused.

## Running it locally

```bash
cd infra && docker compose up -d db          # Postgres
cd server && mvn -DskipTests install          # build all modules
# start the edge (from server/edge)
mvn -pl edge dependency:build-classpath -Dmdep.outputFile=/tmp/vi-cp.txt
java -cp "edge/target/classes:edge/../runtime/target/classes:$(cat /tmp/vi-cp.txt)" \
     com.virtualinvestor.edge.EdgeApplication
```

Then create a round (it starts a live world):

```bash
curl -s -X POST localhost:8080/api/rounds \
  -H 'Content-Type: application/json' -H 'X-API-Version: 1' \
  -d '{"startingCapital":100000,"durationMinutes":30,"preset":"REALISTIC"}'
```

## Tests

- `runtime` — `RunningRoundTest`, `SessionLifecycleTest`, `OrderIntakeValidationTest`,
  `OrderIdempotencyTest`, `FillAccountingTest`, `WorldJournalPersistenceTest`,
  `RoundRecoveryTest`.
- `edge` — `RoundRuntimeServiceTest`, `RoundBroadcasterTest`, `StreamResyncTest`,
  `BotOnlyRoundIntegrationTest`, `WireOrderRoundIntegrationTest`.

Run: `cd server && mvn test` (persistence-backed tests need the compose db).

## Not yet implemented

- The terminal's own order entry (the interface change): the server accepts orders over the
  wire, but the browser terminal does not send them yet.
- Options/futures/CFDs and a persistent always-on world.
