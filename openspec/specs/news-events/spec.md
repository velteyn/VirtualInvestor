# news-events Specification

## Purpose
Defines the news wire: fictional sources, the taxonomy and timing of events, their effect on fundamental value and volatility, and the differentiated ways bots and the market react to them.

## Requirements

### Requirement: News wire delivery

The system SHALL deliver timestamped news items to clients through a wire, each item naming a fictional source, a headline, a body, and the instruments or scopes it concerns. Sources and companies SHALL be fictional.

#### Scenario: Headline reaches the wire

- **WHEN** a news event is released
- **THEN** subscribed clients receive the item with its source, timestamp, and text

### Requirement: Event taxonomy

News events SHALL be classified by scope — macro, sector, company, or market-structure — and each SHALL carry a direction, a magnitude, and a duration describing whether its effect is a discrete pop or a sustained drift.

#### Scenario: Macro event moves the market

- **WHEN** a macro event is released
- **THEN** the market factor shifts and instruments respond according to their factor exposures

#### Scenario: Company event moves one name

- **WHEN** a company event is released for one instrument
- **THEN** that instrument's fundamental value changes by the event magnitude while others are largely unaffected

### Requirement: Scheduled and random events

A round SHALL include both scheduled events known in advance, such as an earnings release time, and random events released during the session, with cadence scaled to the session length.

#### Scenario: Scheduled event occurs at its time

- **WHEN** a scheduled event's time arrives during a session
- **THEN** the event is released then, regardless of price action

#### Scenario: Random events are seeded

- **WHEN** two rounds are created with the same seed and config
- **THEN** the same random events occur at the same times

### Requirement: Rumor and confirmation

A news event MAY be released first as an unconfirmed rumor and later confirmed or denied, and the market SHALL react to the rumor before confirmation.

#### Scenario: Rumor moves price before confirmation

- **WHEN** a rumor is released about an instrument
- **THEN** the instrument's price reacts, and a later confirmation or denial produces a further move

### Requirement: News impact on volatility

A news event SHALL be able to raise the affected market's volatility state for the duration of its effect, increasing spreads and price movement beyond the initial value change.

#### Scenario: Volatility rises after news

- **WHEN** a significant event is released
- **THEN** realized volatility for the affected instruments rises and market-making spreads widen

### Requirement: Differentiated bot reactions

Each rival and institutional participant SHALL react to news according to its strategy, so that different personalities trade the same headline in different directions and sizes.

#### Scenario: Rivals trade a headline differently

- **WHEN** a company event is released
- **THEN** the value-oriented rival, the momentum rival, and the news-driven rival respond in ways consistent with their declared strategies

### Requirement: Fair human cadence

News SHALL be delivered to the human player with a cadence and latency that allow a person to read and react, without granting bots an unfair informational advantage over the player.

#### Scenario: Player can react to news

- **WHEN** a news item is released
- **THEN** the human player receives it in time to place a reaction order before the event's full effect has played out

### Requirement: News signal delivery

The world SHALL hand each tick's news releases to strategy contexts as signals
carrying scope, symbol, sector, and signed magnitude, so headline strategies
fire on the release tick and deterministic replays agree.

#### Scenario: Headline hunter pounces

- **WHEN** a company event above the hunter's threshold is released
- **THEN** the news-driven rival takes in the headline's direction that tick

#### Scenario: Macro moves size through the index

- **WHEN** a macro event is released
- **THEN** the macro institution trades size without one headline firing every engine at once

#### Scenario: Silence stays silent

- **WHEN** no news is released on a tick
- **THEN** headline strategies emit nothing and all other behavior is unchanged
