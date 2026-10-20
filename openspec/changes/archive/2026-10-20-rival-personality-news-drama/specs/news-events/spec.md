# Spec Delta

## MODIFIED Requirements

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

### Requirement: Rumor and confirmation

A news event MAY be released first as an unconfirmed rumor and later confirmed or denied, and the market SHALL react to the rumor before confirmation. Each staged story carries an authored headline triple (rumor-lede, confirm-lede, deny-lede); the wire emits the stage's lede, never repeating the rumor text at resolution.

#### Scenario: Rumor moves price before confirmation

- **WHEN** a rumor is released about an instrument
- **THEN** the instrument's price reacts, and a later confirmation or denial produces a further move

#### Scenario: Resolution reads as a new beat

- **WHEN** a rumor resolves as confirmed or denied
- **THEN** the wire shows the confirm-lede or deny-lede (not the rumor headline), referencing the rumor outcome

## ADDED Requirements

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
