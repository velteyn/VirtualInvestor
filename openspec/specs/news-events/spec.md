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

A news event MAY be released first as an unconfirmed rumor and later confirmed or denied, and the market SHALL react to the rumor before confirmation. Each staged story carries an authored headline triple (rumor-lede, confirm-lede, deny-lede); the wire emits the stage's lede, never repeating the rumor text at resolution.

#### Scenario: Rumor moves price before confirmation

- **WHEN** a rumor is released about an instrument
- **THEN** the instrument's price reacts, and a later confirmation or denial produces a further move

#### Scenario: Resolution reads as a new beat

- **WHEN** a rumor resolves as confirmed or denied
- **THEN** the wire shows the confirm-lede or deny-lede (not the rumor headline), referencing the rumor outcome

### Requirement: News impact on volatility

A news event SHALL be able to raise the affected market's volatility state for the duration of its effect, increasing spreads and price movement beyond the initial value change.

#### Scenario: Volatility rises after news

- **WHEN** a significant event is released
- **THEN** realized volatility for the affected instruments rises and market-making spreads widen

### Requirement: Differentiated bot reactions

Each rival and institutional participant SHALL react to news according to its strategy and personality, so that different personalities trade the same headline in different directions, sizes, and reliability reads. Each bot carries a dispersion parameter scaling its perceived signal as `(1 − 0.01 × dispersion)`, and each release carries a reliability tier (wire, patron, spy) weighting whether that bot acts.

#### Scenario: Rivals trade a headline differently

- **WHEN** a company event is released
- **THEN** the value-oriented rival, the momentum rival, and the news-driven rival respond in ways consistent with their declared strategies

#### Scenario: Dispersion splits the crowd

- **WHEN** the same headline reaches a precise bot (dispersion 0) and a noisy bot (dispersion 30)
- **THEN** the noisy bot perceives 70% of the magnitude and may sit out a borderline headline the precise bot trades, with identical seeds replaying identically

#### Scenario: Spy-tier rumors move fewer desks

- **WHEN** a patron/spy-tier rumor is released
- **THEN** only bots trusting that tier act on the rumor, while wire-only bots wait for confirmation

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

### Requirement: Signal validity windows

Every headline signal SHALL carry a validity window after which headline
reactions stop, while the absorbed fundamental move persists — so fast news
burns out for bots and slow-burn shocks stay tradeable for their duration.

#### Scenario: Expired headlines are never read

- **WHEN** a bot's perception delay hasn't elapsed when a signal's window closes
- **THEN** it never reacts to that headline, as if never read

#### Scenario: Absorbed value survives expiry

- **WHEN** a signal expires
- **THEN** the fundamental repricing it caused remains, and price-based
  strategies continue trading the gap

#### Scenario: Slow shocks outlive fast money

- **WHEN** a long-validity shock (sector supply disruption) is released
- **THEN** delayed institutional takes still fire within the window while
  short-validity earnings stop moving bots after days

### Requirement: Source reliability tiers

Releases SHALL carry a reliability tier of wire, patron, or spy drawn from the round seed, and each tier SHALL have a fixed confirm probability with spy the least reliable, so source choice is flavor with deterministic consequences.

#### Scenario: Spy rumors deny more often

- **WHEN** a round generates spy-tier rumors and wire-tier rumors
- **THEN** the spy tier confirms at a lower rate than wire, and the same seed reproduces the same outcomes

### Requirement: Scope template packs

The generator SHALL narrate staged headlines from per-scope template packs (takeover, regulatory, guru, analyst-color, taunt) with positional subject slots and bodies of at most 200 chars, and the taunt pack SHALL be day-windowed so mid-game banter unlocks by session day.

#### Scenario: Takeover pack fills names

- **WHEN** a company-scope staged story fires
- **THEN** its rumor, confirm, and deny ledes render from the company pack with the subject names in their slots and no real-world names

#### Scenario: Taunts wait for mid-game

- **WHEN** a session is in its opening days
- **THEN** no day-gated taunt template is selected, and the same seed selects the same templates
