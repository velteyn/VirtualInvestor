# Delta — portfolio (change: playtest-fixes)

## ADDED Requirements

### Requirement: History spans checkpoints

Fill history SHALL cover the whole round, not just the tail since the last
checkpoint: checkpoint compaction SHALL delete only clock-scaffolding Tick
rows and keep fills, reports and prints for the history query and post-game
review.

#### Scenario: Early fills survive

- **WHEN** a fill completes and several checkpoints pass
- **THEN** the History section still lists it with price, quantity and fee
