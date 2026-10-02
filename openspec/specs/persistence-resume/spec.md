# persistence-resume Specification

## Purpose
Defines durable state for the simulation: the append-only event log, periodic snapshots, queryable projections, and the recovery behaviors that let a player reconnect, a server restart, or a session be saved and resumed.

## Requirements

### Requirement: Append-only event log

The system MUST persist every world-affecting action — orders, cancels, news releases, bot decisions, and clock advances — as an ordered, append-only event log, and the log MUST be the source of truth for world state.

#### Scenario: Events are ordered

- **WHEN** actions occur during a session
- **THEN** each is appended to the log with a monotonically increasing sequence and tick

#### Scenario: Log is never rewritten

- **WHEN** a correction or recovery is required
- **THEN** the system appends a new event rather than modifying or deleting an existing one

### Requirement: Periodic snapshots

The system SHALL persist a compact snapshot of full world state at a configured interval of ticks or events, so recovery does not require replaying from the start of the round.

#### Scenario: Snapshot limits replay cost

- **WHEN** the world is recovered
- **THEN** it is rebuilt from the latest snapshot plus only the events after that snapshot

### Requirement: Deterministic recovery

Because world state SHALL be a pure function of seed plus event log, the system MUST be able to rebuild any round's state by replaying the log and produce the same state as the original run.

#### Scenario: Server restart recovers state

- **WHEN** the server process restarts during a running session
- **THEN** it loads the latest snapshot, replays subsequent events, and resumes the world clock without losing or duplicating a fill

### Requirement: Player reconnect

The system SHALL allow a disconnected player to reconnect to a live session using a session token, and SHALL resynchronize the player's orders, positions, account, and the current market state.

#### Scenario: Reconnect resyncs the player

- **WHEN** a player reconnects after a dropped connection
- **THEN** the system sends the current order book, positions, account, and market snapshot to the client

### Requirement: Read-model projections

The system SHALL maintain queryable projections — leaderboard, per-participant portfolio and trade history, and round results — derived from the event log and snapshots, and MUST be able to rebuild them from the log.

#### Scenario: Leaderboard query

- **WHEN** a client requests the leaderboard for a round
- **THEN** the system returns each participant's current equity and standing

#### Scenario: Projections rebuild

- **WHEN** a projection is lost or suspected stale
- **THEN** the system rebuilds it from the event log without affecting the live world

### Requirement: Deliberate save and resume

The system SHALL support pausing a session and resuming it later from its stored state, preserving the world clock, every participant's account and positions, and all pending orders.

#### Scenario: Paused session resumed later

- **WHEN** a paused session is resumed after a delay
- **THEN** the world clock and every participant's state continue exactly from the pause point
