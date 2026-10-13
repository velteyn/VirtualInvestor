# Tasks — Series chart history

Core-only, no wire or web changes.

## 1. Derived series history

- [x] 1.1 Add `WarmupGenerator.seriesHistory(universe, spotHistory,
  yearsToBell)` (futures copy OHLC, options map the premium formula with
  put high/low inversion, zero volume, no RNG), and verify
  `WarmupGeneratorTest` covers bar-count parity, determinism, and the put
  inversion.
- [x] 1.2 Merge the derived map into round history at startup so `barBaseSec`,
  the history endpoint, and live continuation cover series, and verify a
  series chart opens with warm-up bars in a round-level test.

## 2. Integration

- [x] 2.1 Run the full server suite green and `openspec validate
  series-chart-history --type change` valid.
