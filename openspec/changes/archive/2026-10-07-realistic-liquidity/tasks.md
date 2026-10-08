# Tasks

## 1. Laddered quoting (core)

- [x] 1.1 Extend the maker quote to sized levels and repost only changed levels; verify a matching test where a large order walks the ladder level by level.
- [x] 1.2 Tier refresh cadences (touch fastest, outer slowest) with per-maker phase offsets; verify unchanged levels emit no events over a quiet window.

## 2. Stress response (core + runtime)

- [x] 2.1 Build the per-symbol toxicity signal from flow imbalance and realized vol; verify it rises on one-sided flow and decays in calm.
- [x] 2.2 Scale maker size/spread per sensitivity down to full withdrawal; verify a stressed book thins, empties at tolerance, and requotes on fade.
- [x] 2.3 Bound event traffic: deepen 1→5 levels and verify order-event volume grows by less than 2x in calibration.

## 3. Markouts and calibration (core)

- [x] 3.1 Fold fill-vs-mid-at-horizon over the journal; verify markouts on a known fill sequence.
- [x] 3.2 Re-baseline bot economics and depth scenarios in `docs/calibration.md`; verify the full suite stays green.

## 4. Verification and docs

- [x] 4.1 End-to-end over a live server: oversized market order walks visible levels, stressed book rejects with reason, markouts recorded; verify each in order.
- [x] 4.2 Document liquidity behavior in `docs/`; verify a fresh reader can predict when size hurts.
