# Spec Delta

## Purpose

Defines the game layer on top of the market: the launch pad that configures a session, the round lifecycle, the participant model of the player, named rival bots and institutions, and the ranking and settlement that decide who is richest.

## ADDED Requirements

### Requirement: Launch pad configuration

The system SHALL present a launch pad before a session begins where the player selects a starting capital, a session duration, and a realism preset, and SHALL validate each selection against allowed ranges.

#### Scenario: Player starts a session

- **WHEN** the player confirms a valid capital, duration, and preset on the launch pad
- **THEN** a new round is created with those settings and the terminal opens on the live market

#### Scenario: Invalid configuration refused

- **WHEN** the player supplies a capital or duration outside the allowed range
- **THEN** the launch pad refuses to start and explains the valid range

### Requirement: Round lifecycle

A round SHALL progress through defined states — created, warm-up, running, closing, settled — and SHALL be identified by a seed and a configuration so it can be reconstructed.

#### Scenario: Round warms up before trading

- **WHEN** a round is created
- **THEN** the system generates market history for the round before the player can trade

#### Scenario: Round settles at the end

- **WHEN** a round's closing bell passes
- **THEN** the round transitions to settled and no further orders are accepted

### Requirement: Participant model

Every trader — the human player, each rival bot, and each institution — SHALL be a participant subject to identical market rules, and a participant SHALL be either human or virtual.

#### Scenario: Bots obey the same rules

- **WHEN** a rival bot places an order
- **THEN** it is validated and matched under the same rules as the human player's orders

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

The system SHALL seed rival bots with the same starting capital the player selected so that final standing by absolute equity is fair, and SHALL also report each participant's return on starting capital.

#### Scenario: Bots mirror the stake

- **WHEN** the player selects a starting capital
- **THEN** each rival begins the round with the same starting capital

#### Scenario: Return is shown alongside equity

- **WHEN** the leaderboard is displayed
- **THEN** each participant shows both current equity and return on starting capital

### Requirement: Margin calls and liquidation

On realism presets that enable margin, the system SHALL issue a margin call and liquidate positions when a participant's equity falls below the maintenance requirement.

#### Scenario: Position liquidated on margin call

- **WHEN** a participant's equity falls below the maintenance margin
- **THEN** the system liquidates enough of the participant's positions to restore the requirement and notifies the participant

### Requirement: Session settlement

At the closing bell the system SHALL mark all positions to the closing price, force-close positions held on intraday margin, compute final equity, and produce the round's ranking.

#### Scenario: Ranking after close

- **WHEN** the closing auction concludes
- **THEN** final equity is computed for every participant and the leaderboard is finalized
