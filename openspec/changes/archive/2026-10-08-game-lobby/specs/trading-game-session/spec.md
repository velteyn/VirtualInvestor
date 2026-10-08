# trading-game-session Specification Delta

## MODIFIED Requirements

### Requirement: Round lifecycle

A round SHALL progress through defined states — created, waiting, warm-up,
running, closing, settled — and SHALL be identified by a seed and a
configuration so it can be reconstructed. Single sessions skip waiting;
rooms hold in waiting with the clock stopped until started.

#### Scenario: Round warms up before trading

- **WHEN** a round is created
- **THEN** the system generates market history for the round before the player can trade

#### Scenario: Room waits for its master

- **WHEN** a room is created
- **THEN** no tick advances until the master starts it or the last seat fills

#### Scenario: Round settles at the end

- **WHEN** a round's closing bell passes
- **THEN** the round transitions to settled and no further orders are accepted
