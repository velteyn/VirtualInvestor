# Tasks

## 1. Signal delivery

- [x] 1.1 Fold each tick's `NewsReleased` events into per-symbol signals in `RunningRound.step` (company → its symbol, sector → its symbols, macro → index only) and pass them through `BotRunner.act` into `BotContext` instead of `null`; verify a unit test where a release makes VULTURE take and silence makes it pass
- [x] 1.2 Verify a macro-flood guard test: one macro release produces at most one hunter take plus one institutional take, never one per engine
- [x] 1.3 Verify determinism is preserved (`OrderFlowDeterminismTest` and same-seed replay still green unmodified)
- [x] 1.4 Add the perception mailbox (per-participant release-tick + delay visibility) and magnitude noise (seeded per news id + participant) in delivery; verify unit tests where VULTURE sees the signal on the release tick, MERIDIAN only after its delay with a jittered magnitude, and identical seeds agree exactly

## 2. Watch and economics

- [x] 2.1 Run the market watch on the economics-baseline seed and verify VULTURE and MERIDIAN both trade (nonzero fees) while gate trips stay zero
- [x] 2.2 Confirm the cascade shape in the watch output (hunter prints precede institutional prints on shared headlines; borderline headlines split the two) and calibrate the delay/noise values against it; record the chosen values
- [x] 2.3 Recalibrate watch bounds only where the measured shift justifies it (reject fraction, empty-side fraction, quoting minimum) and verify the watch passes; record any bound moved with its before/after numbers
- [x] 2.4 Re-baseline bot economics and fee snapshots in `docs/calibration.md` and verify numbers match a fresh run

## 3. Integration

- [x] 3.1 Document headline trading in `docs/news.md` (who reads what, rumor-vs-resolution) and verify the documented behavior matches the implementation
- [x] 3.2 Run the full verification (`cd server && mvn test`, `cd web && npm run build`, `openspec validate --specs`) and verify everything is green
