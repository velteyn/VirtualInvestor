# game-lobby Specification

## Purpose
The meeting place after login: list live games, create solo sessions or held
rooms, join waiting rooms, and let the master start when ready.

## ADDED Requirements

### Requirement: Lobby listing

The system SHALL list live games to any logged-in player: single sessions and
rooms, each with state (waiting/live), preset, time left, seats taken of 6,
and the current leader.

#### Scenario: Lobby shows the world

- **WHEN** an approved player opens the lobby with two live rounds
- **THEN** both appear with state, preset, remaining time, seats and leader

#### Scenario: Empty lobby offers creation

- **WHEN** no games are live
- **THEN** the lobby shows no rows and offers single-player start and room creation

### Requirement: Solo session creation

The system SHALL create a private single-human world on demand exactly as
today, starting at once.

#### Scenario: Solo starts immediately

- **WHEN** a player starts a single-player session
- **THEN** a 1-human world is created and already ticking when the terminal opens

### Requirement: Room creation held waiting

The system SHALL create a room with configuration and human seats declared up
front, build the world without starting its clock, and SHALL record the
creator as room master.

#### Scenario: Created room waits

- **WHEN** a master creates a room
- **THEN** the world exists, no tick advances, and the room lists as waiting with 1 of 6 claimed

### Requirement: Join waiting rooms

The system SHALL let any approved player claim a free seat in a waiting room
in join order, and SHALL refuse joins to live rooms as started.

#### Scenario: Second player joins

- **WHEN** an approved player joins a waiting room with free seats
- **THEN** they hold the next seat and see the updated seat map

#### Scenario: Full room refuses

- **WHEN** a player joins a room with no free seats
- **THEN** the join is refused as full with no state change

#### Scenario: Live room refuses

- **WHEN** a player joins a live room
- **THEN** the join is refused as started with no state change

### Requirement: Master start

The system SHALL start a waiting room's clock on the master's command, and
SHALL auto-start when the last seat fills; starting requires at least the
master seated.

#### Scenario: Master starts a partial room

- **WHEN** the master starts a room with 2 of 6 seats claimed
- **THEN** the clock runs with 2 humans and 4 backfill bots

#### Scenario: Full room starts itself

- **WHEN** the last seat is claimed
- **THEN** the clock runs without a master command

#### Scenario: Stranger cannot start

- **WHEN** a non-master player commands a start
- **THEN** the command is refused and the clock stays held
