# Delta — market-simulation (change: market-scenarios)

## ADDED Requirements

### Requirement: Scenario deck

The news timeline SHALL include rare staged scenarios (takeover bids,
scandals, crashes, squeezes, macro shocks, stampedes) as rumor → confirm/deny
arcs, at most 3 per round, never in the opening 10% of the session and spaced
apart. Scenario magnitudes SHALL reach levels that trip circuit breakers, so
halts occur in live play.

#### Scenario: Takeover arc plays out

- **WHEN** a takeover rumor fires on a held equity
- **THEN** a rumor headline moves the price, and a later confirm or deny
  completes or reverses it, each as its own wire event

#### Scenario: Scenarios stay rare

- **WHEN** ten rounds generate timelines from different seeds
- **THEN** every round holds at most 3 scenarios and none lands in the first
  10% of ticks
