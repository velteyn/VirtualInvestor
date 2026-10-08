# trading-game-session Specification Delta

## MODIFIED Requirements

### Requirement: Participant model

Every trader — each human player, each rival bot, and each institution — SHALL
be a registered player identity, human or virtual, trading through a chosen
bank, and SHALL be subject to identical market rules and identical broker
fees. A round SHALL seat up to 6 ranked humans in join order, backfilling
unclaimed seats first-N by roster order.

#### Scenario: Bots obey the same rules

- **WHEN** a rival bot places an order
- **THEN** it is validated and matched under the same rules as the human player's orders

#### Scenario: Bots pay the same fees

- **WHEN** a bot's fill executes
- **THEN** its bank schedule deducts the identical fee a human would pay on that fill

#### Scenario: Unregistered play impossible

- **WHEN** a socket without an approved identity attempts to trade
- **THEN** its commands are refused

#### Scenario: Second human sits with the bots

- **WHEN** two approved humans hold seats in one round
- **THEN** both trade, rank and settle as distinct participants under identical rules

#### Scenario: Backfill preserves the solo game

- **WHEN** one human plays alone
- **THEN** the ranked roster is that human plus the five named rivals, as before
