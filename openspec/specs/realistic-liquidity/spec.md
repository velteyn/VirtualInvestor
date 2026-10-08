# realistic-liquidity Specification

## Purpose
Books with shape and makers with fear: laddered quotes thin at the touch, stress-driven fade and withdrawal, disciplined refresh, and measured execution quality.

## Requirements

### Requirement: Laddered maker quotes

Makers SHALL quote multiple levels per side sized small at the touch and larger deeper, so aggressive orders visibly walk the book level by level.

#### Scenario: Large order walks

- **WHEN** a market order exceeds the touch size
- **THEN** it fills across successive levels at worsening prices

#### Scenario: Small order untouched

- **WHEN** a market order fits inside the touch size
- **THEN** it fills at one price exactly as before

### Requirement: Stress-driven fade and withdrawal

Makers SHALL shrink size and widen spreads as flow imbalance and realized volatility rise, down to zero (full withdrawal), each maker on its own sensitivity so they never flinch in lockstep. Live withdrawal happens through this fade path plus consumption of resting depth; the tolerance backstop below is a last-resort safety net, not a gameplay path.

#### Scenario: Toxic flow thins the book

- **WHEN** one-sided flow persists
- **THEN** quoted sizes shrink and spreads widen before any halt trips

#### Scenario: Tolerance backstop is a safety net

- **WHEN** stress exceeds a maker's tolerance (unreachable through organic flow: fading thins the book and starves the flow before the score can compound that high)
- **THEN** it posts nothing on the affected side until stress fades, and market orders there may partially fill or reject for no liquidity

### Requirement: Disciplined refresh

Refresh SHALL repost only levels whose price or size materially changed, outer levels SHALL refresh slower than the touch, and makers SHALL stagger cadences — so ladder depth never multiplies event traffic.

#### Scenario: Quiet book, quiet stream

- **WHEN** nothing moves for a full cadence
- **THEN** no cancel/replace events are emitted for unchanged levels

#### Scenario: Bounded traffic growth

- **WHEN** the ladder deepens from 1 to 5 levels per side
- **THEN** order-event volume grows by less than 2x, verified in calibration

### Requirement: Markout measurement

Every fill SHALL record the mid-price N ticks later alongside the fill, so execution quality (paid spread, adverse move) is measurable per fill and usable by calibration.

#### Scenario: Markout recorded

- **WHEN** a fill executes
- **THEN** the mid-price at the configured horizon is captured with it

### Requirement: In-session replenishment

Every ladder rung SHALL requote at least a few times per session, so consumed depth rebuilds while the game is still running — not only the touch.

#### Scenario: Outer depth rebuilds after a sweep

- **WHEN** a sweep consumes the full ladder and stress then fades
- **THEN** deeper rungs requote within the same session and 5-deep depth recovers toward its pre-sweep shape

#### Scenario: Traffic budget holds

- **WHEN** the ladder deepens from 1 to 5 levels per side under the faster cadence
- **THEN** order-event volume still grows by less than 2x, verified in calibration

### Requirement: Depth-aware taking

Aggressive strategies SHALL check resting depth and spread before sending market orders: thin books mean smaller size or standing aside, not blind takes into emptiness.

#### Scenario: Thin book, smaller take

- **WHEN** a strategy's signal fires but resting depth covers only part of its desired size
- **THEN** it takes only what the book affords (or skips) instead of emitting a reject for no liquidity

#### Scenario: Rejects stay rare

- **WHEN** a full session runs on the economics-baseline seed
- **THEN** rejected takes for no liquidity stay a small fraction of executed trades, bounded by the market watch

### Requirement: Fade calibrated to baseline flow

Maker fade points SHALL sit above the mean toxicity of ordinary bot flow, so baseline trading keeps at least two makers quoting on most symbols and only genuine stress thins the book.

#### Scenario: Calm session, full roster quoting

- **WHEN** bots trade normally with no player sweep and no news spike
- **THEN** at least two makers hold resting quotes on most symbols

#### Scenario: Sweep still thins

- **WHEN** a large one-sided sweep lands
- **THEN** quoted sizes still shrink and spreads still widen before any halt trips
