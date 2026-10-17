# Delta — newsroom-narrator (change: narrator-arcs)

## ADDED Requirements

### Requirement: Chained story beats

Scenario arcs SHALL run 3-5 beats linked by a story ID, each beat its own
wire event, with per-beat magnitudes summing to the arc's total move.

#### Scenario: Takeover plays in three acts

- **WHEN** a takeover arc fires
- **THEN** the wire shows approach, bidding-war, and resolution beats that
  reference each other in order

### Requirement: Story memory

Each beat's narration prompt SHALL carry the arc's prior beats (truncated)
and the market consequences since the last beat, so later beats continue the
story instead of restating the headline.

#### Scenario: Part three remembers part one

- **WHEN** the third beat of an arc is narrated
- **THEN** its body references an earlier beat's development by name or
  outcome, verifiable against the stored arc

### Requirement: Scenario bible

Per-scenario authored facts (named facilities, people, figures) SHALL be
the only invented specifics the model may use; everything else stays under
the existing no-invention rules.

#### Scenario: Bible fact appears, nothing else does

- **WHEN** a bible-backed beat is narrated
- **THEN** the body may name bible facts and names no other new specifics
