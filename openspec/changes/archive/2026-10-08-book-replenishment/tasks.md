# Tasks

## 1. Session-relative replenishment cadence

- [x] 1.1 Derive per-rung refresh periods from session length in `BotRunner` (quadratic ratios preserved, every rung requotes ≥3× per session) and verify `QuoteTrafficTest` still passes the <2x budget unmodified
- [x] 1.2 Extend the market watch with a post-sweep ladder-shape sample and verify 5-deep depth on PEAR recovers toward pre-sweep shape within the same session
- [x] 1.3 Update timing-sensitive runtime tests that assume 50-tick waves to derive from the cadence helper and verify `mvn -pl runtime -am test` is green

## 2. Depth-aware taking

- [x] 2.1 Clamp market takes to visible depth in `BotRunner.take` (size to a fraction of touch depth, skip on empty touch or blown-out spread) and verify a unit test where a signal into an empty book emits no order
- [x] 2.2 Run the market watch and verify rejected takes for no liquidity fall to a small fraction of trades while the trade floor holds

## 3. Fade calibration to baseline flow

- [x] 3.1 Measure mean toxicity per symbol on the economics-baseline seed, lower `TOXICITY_FLOW_WEIGHT` so ≥2 makers quote on most symbols in calm, and verify maker unit tests reflect the new weight (updated, not weakened)
- [x] 3.2 Re-run the sweep scenario and verify books still thin (sizes shrink, spreads widen) under genuine stress while gate trips stay zero in the watch

## 4. Watch with teeth

- [x] 4.1 Promote watch telemetry to assertions with calibrated bounds (reject fraction, empty-side fraction, makers-quoting minimum, gate trips == 0) and verify the watch passes on the fixed system
- [x] 4.2 Document the watch run (command, seed, how to read the report) in `docs/liquidity.md` and verify the documented command runs as written

## 5. Economics re-baseline and integration

- [x] 5.1 Re-baseline bot economics and fee/liquidity snapshots in `docs/calibration.md` on the fixed parameters and verify numbers match a fresh run
- [x] 5.2 Run the full verification (`cd server && mvn test`, `cd web && npm run build`, `openspec validate --specs`) and verify everything is green
