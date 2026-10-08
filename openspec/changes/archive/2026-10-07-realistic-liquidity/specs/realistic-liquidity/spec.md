# realistic-liquidity Specification

## Purpose
Books with shape and makers with fear: laddered quotes thin at the touch,
stress-driven fade and withdrawal, disciplined refresh, and measured
execution quality.

## ADDED Requirements

### Requirement: Laddered maker quotes

Makers SHALL quote multiple levels per side sized small at the touch and
larger deeper, so aggressive orders visibly walk the book level by level.

#### Scenario: Large order walks

- **WHEN** a market order exceeds the touch size
- **THEN** it fills across successive levels at worsening prices

#### Scenario: Small order untouched

- **WHEN** a market order fits inside the touch size
- **THEN** it fills at one price exactly as before

### Requirement: Stress-driven fade and withdrawal

Makers SHALL shrink size and widen spreads as flow imbalance and realized
volatility rise, down to zero (full withdrawal), each maker on its own
sensitivity so they never flinch in lockstep.

#### Scenario: Toxic flow thins the book

- **WHEN** one-sided flow persists
- **THEN** quoted sizes shrink and spreads widen before any halt trips

#### Scenario: Extreme stress empties a side

- **WHEN** stress exceeds a maker's tolerance
- **THEN** it posts nothing on the affected side until stress fades, and market
  orders there may partially fill or reject for no liquidity

### Requirement: Disciplined refresh

Refresh SHALL repost only levels whose price or size materially changed, outer
levels SHALL refresh slower than the touch, and makers SHALL stagger
cadences — so ladder depth never multiplies event traffic.

#### Scenario: Quiet book, quiet stream

- **WHEN** nothing moves for a full cadence
- **THEN** no cancel/replace events are emitted for unchanged levels

#### Scenario: Bounded traffic growth

- **WHEN** the ladder deepens from 1 to 5 levels per side
- **THEN** order-event volume grows by less than 2x, verified in calibration

### Requirement: Markout measurement

Every fill SHALL record the mid-price N ticks later alongside the fill, so
execution quality (paid spread, adverse move) is measurable per fill and
usable by calibration.

#### Scenario: Markout recorded

- **WHEN** a fill executes
- **THEN** the mid-price at the configured horizon is captured with it
