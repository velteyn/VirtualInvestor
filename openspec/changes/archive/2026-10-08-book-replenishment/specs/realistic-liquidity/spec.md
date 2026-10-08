# Spec Delta

## ADDED Requirements

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
