# Tasks — Market scenarios

## 1. Deck + planner

- [x] 1.1 Scenario deck: templates, magnitudes, staging (rumor fraction,
  resolution delay, confirm/deny bias) per scenario above.
- [x] 1.2 Planner wiring: 1-3 scenarios per round by seed, never in the
  first 10%, spaced ≥15% of session apart; existing earnings + noise
  untouched.

## 2. Tests + validation

- [x] 2.1 Unit: deck ranges, staging ticks, determinism (same seed, same
  deck); rarity cap holds across seeds.
- [x] 2.2 Soak: stepped rounds show rumor → resolution pairs with
  |magnitude| ≥ 0.08 and at least one breaker trip on REALISTIC.
- [x] 2.3 `openspec validate` green; specs record the deck.

## 3. Live

- [x] 3.1 Fresh round: scenario headlines on the wire with staged
  resolution (observed, not forced).
