# Tasks — Futures + Options

Design Open Questions are config-tunable with recorded defaults (ATM ± 3
strikes; index gets futures, not options in v1) — none changes the build.

## 1. Series in the instrument universe

- [ ] 1.1 Add FUTURE and OPTION to InstrumentType and list series in
  InstrumentUniverse at round creation (one future per tradeable underlying +
  index; ATM ± 3 call/put strikes per tradeable equity), and verify
  `InstrumentUniverseTest` covers listing, metadata, and unknown-series lookup.
- [ ] 1.2 Extend MarketRules with per-series tick, quantity grid, and
  initial/maintenance margin plus underlying-halt propagation, and verify
  `MarketRulesTest` covers series grids and halt inheritance.
- [ ] 1.3 Expose series in SymbolSearch (chain query by underlying) and verify
  search returns FUT/OPT asset classes with metadata.

## 2. Deterministic pricing

- [ ] 2.1 Implement the closed-form option premium function of (underlying
  price, strike, time-to-bell, baseVolatility) as a pure function, and verify
  identical inputs yield identical premia plus put-call parity sanity in
  `OptionPricingTest`.
- [ ] 2.2 Define the futures carry reference and series mark rule (last trade,
  formula/quote fallback when untraded), and verify marks are deterministic in
  `FuturesMarkTest`.

## 3. Account margin accounting

- [ ] 3.1 Add lockedMargin to Account with lock/release, extend equity() and
  buyingPower() per D5, and verify `AccountTest` covers lock, release, and
  buying-power reduction.
- [ ] 3.2 Add the futures fill path (no notional exchange, entry tracking, MTM
  through unrealized) and route option fills through premium settlement, and
  verify `AccountDerivativesTest` covers open/mark/close/settlement P&L plus
  per-symbol attribution parity with the lump sum.
- [ ] 3.3 Spawn one InstrumentEngine per listed series symbol at round setup,
  and verify orders on a series match, rest, and print independently of the
  underlying book in `SeriesBookTest`.

## 4. Intake validation and fees

- [ ] 4.1 Extend OrderValidator with series existence, expiry, series grids,
  and halted-underlying reasons, and verify each reason in
  `OrderValidatorDerivativesTest`.
- [ ] 4.2 Add percentage-of-notional futures/options commissions and ticket minima to
  bank schedules with the deterministic deduction rule, and verify replay
  identity in `BrokerageDerivativesTest`.
- [ ] 4.3 Emit expiry/exercise close events (FuturesSettled, OptionSettled with
  series, quantity, settlement price) through GameEvent + codec, and verify
  round-trip serialization in `GameEventCodecTest`.

## 5. Risk, margin calls, settlement

- [ ] 5.1 Extend MarginManager.check to notional exposure (futures) and covered
  short-option exposure with derivatives-aware forceClose, and verify call,
  liquidate-most-levered-first, and floor-flatten in `MarginManagerTest`.
- [ ] 5.2 Implement the deterministic settlement order (auction → futures
  settle → options exercise/expire → equity mark + forced closes → ranking) in
  Settlement, and verify a mixed book settles with nothing surviving the bell
  in `SettlementDerivativesTest`.
- [ ] 5.3 Enforce notional-based sizing caps and the joint loss bound
  (margins + calls + floor) in risk limits, and verify bounded final equity
  for a levered losing bot in `RiskLimitsDerivativesTest`.

## 6. Liquidity and bots

- [ ] 6.1 Quote futures and within-one-step options in MarketMakerStrategy
  around carry/formula values, and verify quotes track the reference in
  `MarketMakerDerivativesTest`.
- [ ] 6.2 Confirm rival strategies stay equities-only under identical rules and
  verify an equities-only bot round still passes unchanged.

## 7. Edge and terminal

- [ ] 7.1 Extend FillHistory with premium/margin effects and settlement-close
  entries, and verify the History section shows them after reconnect.
- [ ] 7.2 Add series picker, chain display, and margin/premium preview with
  guards to the order ticket, and verify transmit-disable reasons in ticket
  tests.
- [ ] 7.3 Show expiry marking, MTM/premium values, and the locked-margin
  breakout in positions and account panels, and verify `npm run build` passes
  for web.

## 8. Integration and determinism

- [ ] 8.1 Extend the calibration/determinism harness (WorldStateHasher,
  benchmarks) to rounds with derivatives books, and verify seed+log replay
  reproduces every futures balance, premium, and fee exactly.
- [ ] 8.2 Run the full server suite (`cd server && mvn test`) plus web build
  green, and verify `openspec validate futures-options --type change` is valid.
- [ ] 8.3 Update `docs/future-ideas.md` Tier 4 options item as delivered by
  this change, and verify the doc links to the change.

## Workflow follow-up

- Implement via `/opsx-apply`, then archive the change (specs sync, per-feature
  commit) before starting bank-loan margin or crew.
