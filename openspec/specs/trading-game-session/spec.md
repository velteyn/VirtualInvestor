# trading-game-session Specification

## Purpose
Defines the game layer on top of the market: the launch pad that configures a session, the round lifecycle, the participant model of the player, named rival bots and institutions, and the ranking and settlement that decide who is richest.

## Requirements

### Requirement: Launch pad configuration

The system SHALL present a launch pad before a session begins where the player selects a starting capital, a session duration, and a realism preset, and SHALL validate each selection against allowed ranges.

#### Scenario: Player starts a session

- **WHEN** the player confirms a valid capital, duration, and preset on the launch pad
- **THEN** a new round is created with those settings and the terminal opens on the live market

#### Scenario: Invalid configuration refused

- **WHEN** the player supplies a capital or duration outside the allowed range
- **THEN** the launch pad refuses to start and explains the valid range

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

### Requirement: Visible rival bots

A round SHALL include five named rival bots with distinct, visible identities and strategies, and the leaderboard SHALL show each rival's identity and standing.

#### Scenario: Rival roster present

- **WHEN** the player views the leaderboard during a session
- **THEN** all five rivals are listed with names and current equity

### Requirement: Institutions as market

The system SHALL include institutional participants that provide liquidity and respond to news and macro conditions, and institutions SHALL NOT appear as ranked rivals on the player's leaderboard.

#### Scenario: Institution supplies liquidity

- **WHEN** the book for a liquid instrument is thin
- **THEN** institutional participants may add resting liquidity around the fundamental value

#### Scenario: Institutions are not rivals

- **WHEN** the player views the leaderboard
- **THEN** institutional participants are absent from the ranked standings

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

### Requirement: Margin calls and liquidation

On realism presets that enable margin, the system SHALL issue a margin call and liquidate positions when a participant's equity falls below the maintenance requirement. Equity for the call SHALL include futures variation and option
market value; liquidation SHALL flatten every position, closing the most levered
exposure first (futures at current series price, options at current premium or
intrinsic, alongside equity positions).

#### Scenario: Position liquidated on margin call

- **WHEN** a participant's equity falls below the maintenance margin
- **THEN** the system liquidates enough of the participant's positions to restore the requirement and notifies the participant

#### Scenario: Derivatives liquidated first when most levered

- **WHEN** a margin call fires on an account whose largest exposure is futures
- **THEN** the futures position closes before equity positions; a call still
  flattens every position

### Requirement: Session settlement

At the closing bell the system SHALL mark all positions to the closing price, force-close positions held on intraday margin, compute final equity, and produce the round's ranking. The system SHALL first expire all futures and option
series in cash against their underlyings' closing prices; settlement order SHALL
be deterministic: closing auction prices first, then derivatives expiry and
exercise, then equity marking and forced closes.

#### Scenario: Ranking after close

- **WHEN** the closing auction concludes
- **THEN** final equity is computed for every participant and the leaderboard is finalized

#### Scenario: Derivatives settle before ranking

- **WHEN** the closing auction concludes with open futures and option positions
- **THEN** expiry and exercise payments are applied before final equity and
  ranking are computed
