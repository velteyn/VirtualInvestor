# trading-game-session Specification Delta

## Modified Requirements

### Requirement: Participant model

Every trader — the human player, each rival bot, and each institution — SHALL
be a registered player identity, human or virtual, trading through a chosen
bank, and SHALL be subject to identical market rules and identical broker fees.

#### Scenario: Bots obey the same rules

- **WHEN** a rival bot places an order
- **THEN** it is validated and matched under the same rules as the human player's orders

#### Scenario: Bots pay the same fees

- **WHEN** a bot's fill executes
- **THEN** its bank schedule deducts the identical fee a human would pay on that fill

#### Scenario: Unregistered play impossible

- **WHEN** a socket without an approved identity attempts to trade
- **THEN** its commands are refused

### Requirement: Fair ranking

The system SHALL seed rival bots with the same starting capital the player
selected and SHALL rank by equity net of all broker fees, and SHALL also
report each participant's return on starting capital.

#### Scenario: Bots mirror the stake

- **WHEN** the player selects a starting capital
- **THEN** each rival begins the round with the same starting capital

#### Scenario: Return is shown alongside equity

- **WHEN** the leaderboard is displayed
- **THEN** each participant shows both current equity and return on starting capital

#### Scenario: Equal footing at the open

- **WHEN** a round starts
- **THEN** every ranked participant holds the same starting capital at the same bank terms or better

#### Scenario: Ranking reflects net wealth

- **WHEN** the round settles
- **THEN** the winner is the highest equity after fees, with returns shown per participant
