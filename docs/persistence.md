# Persistence and recovery

Durable state for the simulation. Implementation lives in the `persist` module; it depends on
`core` for event types and the codec.

## Source of truth: the event log

Because the world is a pure function of the round seed and the ordered event log, the log is the
source of truth. Everything else can be rebuilt.

- `events` table (`round_id`, `seq`, `tick`, `type`, `payload`) is **append-only**. There is no
  update or delete path, and the primary key makes a duplicate sequence number fail
  (`JdbcEventLog`, `EventLogTest`).
- `BatchedEventLogWriter` buffers events and flushes in batches so the simulation hot path never
  blocks on a database round-trip per event. It assigns sequence numbers monotonically from the
  log's high-water mark.
- Payloads are encoded by the dependency-free `RecordTextCodec`; `GameEventCodec` maps a stored
  event back to its record type via a registry derived from the sealed `GameEvent` interface.

## Snapshots

Snapshots bound recovery cost so a restart never replays from tick zero.

- A snapshot is the ordered event prefix up to a sequence number (`WorldSnapshot`), stored in the
  `snapshots` table keyed by (`round_id`, `tick`) through `SnapshotStore` / `JdbcSnapshotStore`.
- `WorldStateCodec` encodes/decodes a list of events. Since "state at seq N" is exactly "the
  first N events", snapshots are compact and codec-consistent with the log.
- Recommended cadence: every ~10 seconds or every N thousand events, whichever comes first
  (a pure tradeoff; no architecture change).
- Each checkpoint compacts the consumed prefix (`DELETE ... WHERE seq < snapshot seq`,
  keeping one boundary row so numbering never restarts into reuse) — without this,
  every checkpoint re-reads the whole history and stalls the tick past ~600 ticks.
  Round ids carry a per-boot tag (`round-N-ab12`) so restarts never collide with
  old rows. A failed checkpoint logs loudly and skips: persistence never kills
  the market.

## Recovery

`Recovery.recover(log, snapshots)` loads the latest snapshot and replays events strictly after
its sequence. `RoundRecoveryService.recover()` additionally inspects the history to decide
whether the round was running, paused or settled, and returns the tick to resume from.

Verify locally:

```bash
cd server && mvn -q -pl persist -am test
# requires the compose db:
cd infra && docker compose up -d db
```

## Read models

Projections (`Projection`, `PortfolioProjection`, `LeaderboardProjection`,
`ProjectionBuilder`) are disposable: they fold the log into queryable shapes and can always be
rebuilt. They are never the source of truth.

## Reconnect

A disconnected player reclaims their seat with a session token (`SessionToken`, hashed and
compared in constant time). `ReconnectService.reconnect` returns a `ResyncPayload` containing
only the events the client missed since its last seen sequence, so it can reconcile orders,
positions, account and market view.

## Pause and resume

A deliberate pause appends a `RoundPaused` event and writes a checkpoint; resume appends
`RoundResumed`. A restart rebuilds history, sees the pause, and resumes paused (or running)
exactly where it stopped.

## Schema

Flyway migrations live in `persist/src/main/resources/db/migration`:

- `V1__init.sql` — `rounds`, `participants`, `accounts`, `positions`, `orders`, `fills`,
  `events`, `snapshots`, `results`.
- `V2__payload_text.sql` — payload/state columns as text for the codec.

## Not yet implemented

- Token expiry and rotation for reconnect.
- Snapshot cadence tuning and a storage benchmark for per-tick `BookChanged` events.
- Wiring persistence into the live world loop (Group 10 and beyond).
