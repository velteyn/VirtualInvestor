# world-loop Specification

## Purpose
Defines the server-side running round: the fixed-step loop that advances the market, the news and the bots, enforces market rules and session phases, appends every event to the durable log, and can be paused, resumed and recovered.

## Requirements

### Requirement: Round start and warm-up

Creating a round SHALL transition it from `CREATED` through `WARMUP` to `RUNNING`: the warm-up SHALL generate history from the fundamental process and seed each instrument's book before the loop starts.

#### Scenario: Round begins running

- **WHEN** a round is created with a valid configuration
- **THEN** history and books are generated, the round enters `RUNNING`, and the loop begins ticking

### Requirement: Fixed-step deterministic tick

The loop SHALL advance the world in fixed steps from the round seed, and the world state SHALL be a pure function of the seed and the ordered event log, so the same round can be reconstructed.

#### Scenario: Same seed reproduces the run

- **WHEN** a stored round's events are replayed from the seed
- **THEN** every price, fill and account balance matches the original run

### Requirement: Tick drives the world

On each tick the loop SHALL advance the fundamental model and volatility state, release any due news, let every bot strategy act, apply market rules, and update session phase — in a fixed, documented order.

#### Scenario: Bots act each tick

- **WHEN** the continuous session is running
- **THEN** each rival and institution evaluates its strategy and may place orders

#### Scenario: News releases on schedule

- **WHEN** a scheduled or random news event is due at a tick
- **THEN** the news engine applies its impact and the loop emits the news event

### Requirement: Session phases gate trading

The loop SHALL track the session phase (`PRE_OPEN`, `OPENING_AUCTION`, `CONTINUOUS`, `CLOSING_AUCTION`, `CLOSED`) and SHALL only accept continuous trading during `CONTINUOUS`, running auctions at their phase boundaries.

#### Scenario: No continuous trading before the open

- **WHEN** the phase is `PRE_OPEN` or an auction
- **THEN** continuous orders are not accepted and the phase's own mechanism governs the price

### Requirement: Margin enforcement each tick

On presets that enable margin, the loop SHALL check each participant's equity against the maintenance requirement every tick and liquidate as needed, emitting the margin and liquidation events.

#### Scenario: Leveraged account liquidated

- **WHEN** a participant's equity falls below the maintenance requirement
- **THEN** the loop issues a margin call and liquidates the positions at their marks

### Requirement: Events are appended as the world runs

The loop SHALL append every world-affecting event it produces to the round's append-only event log, in sequence and tick order, without blocking the tick on I/O, and SHALL checkpoint periodically.

#### Scenario: Events persist without stalling the loop

- **WHEN** the loop advances many ticks
- **THEN** events are batched to the log and periodic snapshots are written, and tick timing is not blocked on the database

### Requirement: Pause, resume and recover

The loop SHALL honor `pause` and `resume` on a running round, and SHALL rebuild state from the latest snapshot plus later events on recovery, resuming paused or running exactly where it stopped.

#### Scenario: Restart resumes mid-round

- **WHEN** the process restarts during a running round
- **THEN** the loop reloads from the snapshot, replays later events, and continues the clock with no lost or duplicated fill

#### Scenario: Paused round stays paused

- **WHEN** a round is paused and the process later resumes it
- **THEN** the loop continues from the pause point without advancing the clock while paused

### Requirement: Settlement at the close

At the closing bell the loop SHALL mark positions to the closing price, force-close them, compute final equity, emit `RoundSettled`, and stop.

#### Scenario: Round settles

- **WHEN** the closing auction finishes
- **THEN** all positions are closed, final standings are computed, and no further orders are accepted
