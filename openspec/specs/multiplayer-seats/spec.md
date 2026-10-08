# multiplayer-seats Specification

## Purpose
More than one human per round: extra humans join the fixed bot roster as
ordered seats with per-seat purses, and rooms seal at start.

## Requirements

### Requirement: Roster injection

The system SHALL accept additional approved humans at round construction
beside the fixed bot roster, each funded at the round's starting capital.

#### Scenario: Second human sits down

- **WHEN** a round is built with two approved humans
- **THEN** both trade as distinct participants with equal starting capital

#### Scenario: Solo shape preserved

- **WHEN** a round is built with one human
- **THEN** the roster is exactly today's 1 human, 5 rivals and 3 institutions

### Requirement: Join-order seats with bot backfill

The system SHALL seat humans in join order (`human`, `human-2`, …) up to 6
ranked seats and SHALL fill unclaimed seats first-N by roster order, keeping
bot-to-bank assignment by bot name.

#### Scenario: Backfill covers empty seats

- **WHEN** two humans hold seats 1 and 2 at start
- **THEN** bots MARK, TIDE, LEDGER and BLITZ hold seats 3 to 6 with their usual banks

#### Scenario: Full house

- **WHEN** six humans hold every seat
- **THEN** no bot sits ranked and the round still starts

### Requirement: Per-seat purse mapping

The system SHALL map each human seat to its player for purse reset at
creation and write-back at settlement, independently per seat.

#### Scenario: Purses do not mix

- **WHEN** two humans finish a round with different results
- **THEN** each stored purse matches its own seat's final account

### Requirement: Seated binding

The system SHALL bind a joining socket to the seat its login token maps to in
that round, and SHALL refuse with not-approved when the token is unknown or
its player holds no seat. The clock-based `started` lock lives in `game-lobby`,
where held rounds give it meaning.

#### Scenario: Seat binds by token

- **WHEN** an approved player joins a round that seated them at creation
- **THEN** their commands route to their own seat's participant

#### Scenario: Stranger refused

- **WHEN** an approved player joins a round that did not seat them
- **THEN** the join is refused as not-approved with no binding
