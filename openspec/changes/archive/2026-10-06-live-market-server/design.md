# Design

## Context

See `proposal.md` for motivation and `specs/*/spec.md` for requirements. The pieces to compose
already exist and are tested: the framework-free core (`WorldClock`, `RealTimeDriver`,
`FundamentalModel`, `VolatilityState`, `InstrumentEngine`, `NewsEngine`/`NewsGenerator`,
`InstrumentUniverse`/`SymbolSearch`, the five rival strategies and institutions, `SessionSchedule`/
`SessionStateMachine`, `OrderValidator`/`CircuitBreaker`/`ShortSalePolicy`, `MarginManager`,
`Settlement`, `Ranking`, `TradeGateway`), the `persist` module (`JdbcEventLog`,
`BatchedEventLogWriter`, `JdbcSnapshotStore`, `RoundRecoveryService`, `Recovery`,
`SessionRegistry`), and the `edge` (Spring Boot 4.1.1, REST + `/ws`, `WireMessage`,
`WireMapper`, `MarketBroadcaster`, `GameService`). What is missing is the thing that makes them
run together: a **world runtime**.

## Goals / Non-Goals

**Goals:**

- A single, deterministic, server-side running round that composes the core and streams its
  output.
- Order intake that any participant (human or bot) reaches through one path, with fills
  applied to accounts.
- Durability as the world runs, and recovery/pause/resume.

**Non-Goals:**

- No terminal/UI work (separate change).
- No new market model; the core is used as-is.
- No multi-round scale-out or persistent always-on world.

## Decisions

### D1. A `world` runtime module owns the running round

**Decision:** Add a framework-free `world` module (or a `runtime` package in `game`) that
composes the core into a `RunningRound`. Spring only starts/stops it and marshals commands onto
it.

**Rationale:** Keeps the hot loop out of Spring (design D2 already), testable headlessly, and
deterministic. The edge stays thin.

**Alternatives:** put the loop in a Spring `@Scheduled` bean (rejected: proxies/timers on the
hot path, hard to test deterministically).

### D2. One thread owns the world; the edge posts commands to it

**Decision:** The loop runs on one thread driven by `RealTimeDriver`. Client commands arrive on
the edge and are enqueued into a single-producer queue the loop drains at the top of each tick.
Broadcasts are published after the tick.

**Rationale:** Single-writer state removes locking and keeps determinism. The queue is the only
cross-thread touchpoint.

**Alternatives:** synchronize every structure (rejected: contention, non-deterministic
interleaving); actor per instrument (deferred: more moving parts than needed now).

### D3. Command intake maps wire commands to core commands

**Decision:** Add client → server order commands to `WireMessage`; the edge validates the
envelope and enqueues a core `Command.PlaceOrder`/`ModifyOrder`/`CancelOrder`; the loop applies
it through `TradeGateway` + `OrderValidator`, updates accounts, and emits events.

**Rationale:** Reuses the sealed `Command`/`GameEvent` set and the existing `TradeGateway`
participant path; the wire stays a thin DTO layer translated by `WireMapper`.

### D4. Fills are applied to accounts by the intake, then emitted

**Decision:** On each fill, apply to both counterparties' `Account` (cash, position, realized
P&L) before emitting the trade print and order-status events.

**Rationale:** Keeps the ledger consistent with the emitted stream; a stream replay reproduces
the accounts.

### D5. Persistence rides the loop, off the hot path

**Decision:** The loop appends its events through `BatchedEventLogWriter` (batch flush at tick
or size boundaries) and calls `RoundRecoveryService.checkpoint` on a cadence. Startup:
`RoundRecoveryService.recover()` rebuilds state before the loop resumes.

**Rationale:** Directly satisfies the world-loop requirements and reuses the tested persist
module.

### D6. Streaming is a publisher the loop calls

**Decision:** The loop hands produced events to a `RoundPublisher` that maps core events to wire
payloads (`WireMapper.event`) and hands them to `MarketBroadcaster`. Depth is pushed through the
existing per-symbol coalescing.

**Rationale:** The broadcaster already coalesces and tolerates slow sessions; the loop never
blocks on a socket.

### D7. Session phases drive who may act

**Decision:** `SessionStateMachine` gates the loop: auctions run at boundaries, continuous
trading only in `CONTINUOUS`, margin checked each tick, settlement at the close.

**Rationale:** Matches the market-simulation and trading-game-session behavior already built.

## Risks / Trade-offs

- **Determinism vs. live clocks** → The loop is fixed-step; the real-time driver only decides
  *when* to step. Keep all randomness on seeded streams and all wall-clock use in the driver.
- **Command latency** → Enqueuing commands and applying at the next tick adds ≤1 tick latency;
  acceptable and fair (bots share it).
- **Backpressure** → A slow subscriber must not stall the loop. `MarketBroadcaster` already
  drops failing sessions; cap queue sizes.
- **Recovery correctness** → Every world-affecting action must be in the log; a missed action
  breaks replay. Mitigation: the golden-master hash test after recovery.
- **Scope** → This is large; sequence it (tasks) so the loop and intake land before streaming
  polish.

## Migration Plan

Additive: a new module/package plus small edge additions (new message types, a runtime starter).
Existing tests are unaffected. Rollback is removing the runtime bean; the core, persist and edge
tests keep passing.

## Open Questions

- Tick rate under load with 21 instruments and ~8 bots — tune the default step (100 ms) after a
  benchmark; deferrable, does not change the specs.
- Whether bots act every tick or on staggered schedules — a performance/realism knob, deferrable.
- Snapshot cadence — reuse the ~10 s default from `docs/persistence.md`; tune later.
