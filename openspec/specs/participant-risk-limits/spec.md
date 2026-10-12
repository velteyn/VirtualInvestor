# participant-risk-limits Specification

## Purpose

Defines how a participant's trading is bounded by its equity, so no participant — bot or human — can be driven to losses far beyond its capital or keep trading a losing strategy unchecked.

## Requirements

### Requirement: Orders sized relative to equity

Each participant SHALL size its orders as a bounded fraction of its current equity, so order size scales with the account rather than being a fixed quantity independent of it. For derivatives, sizing SHALL be assessed on
notional exposure (futures: price times multiplier times quantity; options:
premium times quantity for longs, strike-based exposure for shorts) against
the same fraction bounds, with margin availability as an additional hard cap.

#### Scenario: Size falls as equity falls

- **WHEN** a participant's equity halves
- **THEN** its order sizes fall proportionally rather than staying constant

#### Scenario: Size is capped

- **WHEN** a participant has ample equity
- **THEN** its order size is capped at a maximum so no single order is unbounded

#### Scenario: Oversized futures order refused

- **WHEN** a futures order's notional exceeds the bounded fraction of equity
- **THEN** the order is refused with a sizing reason

#### Scenario: Short option blocked without margin

- **WHEN** a short option order would exceed available margin for its exposure
- **THEN** the order is refused with a margin reason

### Requirement: A participant is stopped at a risk floor

A participant SHALL stop opening new positions when its equity falls to or
below a risk floor, and SHALL be flattened at that point, so it cannot continue
a losing strategy into deeper negative equity. Equity for the floor SHALL
include futures variation and option market value, and flattening SHALL close
derivative positions alongside equity positions.

#### Scenario: Losing participant stops and flattens

- **WHEN** a participant's equity reaches the risk floor
- **THEN** it stops trading and its positions are closed

#### Scenario: Stopped participant takes no further risk

- **WHEN** a participant has been stopped
- **THEN** it submits no further orders for the rest of the round

#### Scenario: Derivatives MTM trips the floor

- **WHEN** adverse futures variation pushes equity to the risk floor
- **THEN** the participant is stopped and all positions including derivatives
  are flattened

### Requirement: Losses are bounded by capital

Across a round, a participant's losses SHALL be bounded by a multiple of its
starting capital close to one, so no participant ends the round at many
multiples of its capital in debt. Margin requirements, maintenance calls, and
the risk-floor flatten SHALL jointly enforce the bound despite notional
leverage; short-option exposure SHALL always be margin-covered within the same
bound.

#### Scenario: Worst-case final equity is near zero, not deeply negative

- **WHEN** a rival bot plays a consistently losing strategy for a full round
- **THEN** its final equity is at worst a small negative amount, not several times its starting capital

#### Scenario: Levered loss still bounded

- **WHEN** a round with futures and short-option trading settles
- **THEN** no participant's loss exceeds the configured multiple of starting
  capital

### Requirement: The leaderboard ranks responsibly

A stopped participant SHALL remain visible on the leaderboard with its final equity, and ranking SHALL handle negative equity deterministically.

#### Scenario: Stopped participant still ranked

- **WHEN** a participant is stopped
- **THEN** it still appears in the standings with its equity and returned fraction
