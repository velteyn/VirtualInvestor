# Delta — market-simulation (change: dead-market-makers)

## ADDED Requirements

### Requirement: Valid quote and order prices

Maker ladders SHALL NOT post rungs with non-finite or non-positive prices
(far-OOM rungs are skipped, not quoted), and the matching engine SHALL reject
LIMIT orders carrying non-finite or non-positive limit prices instead of
resting or matching them.

#### Scenario: Far-OOM rung skipped

- **WHEN** a maker's series reference rounds a ladder rung to zero or below
- **THEN** no quote is posted for that rung and the tick completes normally

### Requirement: Total fill accounting

Applying a fill SHALL NOT throw on any price: fee notionals are clamped to
finite non-negative values, so one bad print can neither abort the tick nor
drop the tick's events.

#### Scenario: Bad-price fill cannot kill the tick

- **WHEN** a fill arrives with a non-finite or negative price
- **THEN** accounting completes, the events broadcast, and the round keeps
  ticking
