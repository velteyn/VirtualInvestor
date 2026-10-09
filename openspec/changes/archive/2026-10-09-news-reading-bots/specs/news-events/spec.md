# Spec Delta

## ADDED Requirements

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
