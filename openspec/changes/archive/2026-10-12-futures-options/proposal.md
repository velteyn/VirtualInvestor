# Proposal — Futures + Options (change: futures-options)

## Why

VirtualInvestor trades one asset class: unlevered fictional equities with losses
bounded near 1x capital (`participant-risk-limits`, `docs/future-ideas.md` thin
spots). There is no leverage and no convexity, so hedging, vol trading, and
end-of-session gamma drama do not exist. Wall $treet Trader 2001 — the game's
stated inspiration for this arc — listed its tradeable set as "Shares, Futures
and Call and Put Options" (MYTH NFO), with T-Bills and interest-rate sensitivity
shaping prices (GameSpot review of WST 2000). Adding futures and options brings
that dimension while the pure-intraday session softens the hardest part:
everything can expire cash-settled at the closing bell. Bank-loan margin and the
hireable crew (analyst/spy/lawyer/press agent) stay out of this change and follow
as later changes in the same arc.

## What Changes

- **Futures on existing underlyings**: cash-settled intraday contracts per equity
  (and optionally the index), quoted off the equity book, expiring at the closing
  auction price. Linear payoff with notional leverage through margin.
- **Calls and puts**: European-style, one expiry per round at the closing bell,
  cash-settled against the closing auction price. A small listed set of strikes
  per underlying (e.g. ATM ± fixed steps), premium paid/received in cash at fill.
- **Deterministic pricing**: futures basis and option premia are pure functions of
  (seed, event log, underlying state) — no RNG outside the world stream, so
  replays stay exact per `market-simulation` determinism.
- **Margin reuses existing rails**: initial/maintenance requirements extend the
  `market-simulation` market-rules margin fields and the `trading-game-session`
  margin-call/liquidation behavior; no overnight positions, no physical delivery.
- **Same intake path**: derivatives orders travel the single `order-intake` path
  with explicit rejection reasons (wrong grid, unknown series, expired, halted
  underlying); fills apply premium/margin to both counterparties before events.
- **Settlement**: at the closing bell all futures MTM to the closing price and all
  options auto-exercise-or-expire cash-settled; forced close of anything left, per
  existing settlement behavior extended to derivatives.
- **What is NOT in scope**: bank loans / cross-round leverage, American exercise,
  multi-expiry chains (the original offered selectable expiries and strikes —
  v1 fixes one expiry at the bell with a small strike set; multi-expiry is an
  explicit follow-up), Greeks display beyond premium, rival-bot derivatives
  strategies (bots keep identical rules and may simply not trade the new books),
  real company names (fictional universe only, per `market-simulation`).

## Capabilities

### New Capabilities

- `futures`: intraday cash-settled futures series — listing, margining,
  mark-to-market, expiry settlement, halts inherited from the underlying.
- `options`: intraday European call/put series — listing (strikes/expiry),
  premium settlement at fill, auto-exercise cash settlement at the bell.

### Modified Capabilities

- `market-simulation`: instrument universe gains derivative series; market rules
  gain futures/options margin, tick/quantity grids, halt propagation, and
  closing-auction settlement prices for derivatives.
- `order-intake`: validation and matching accept derivatives orders and series
  identifiers; lifecycle events cover expiry/exercise-driven closes.
- `portfolio`: positions, average price/premium, MTM, and realized P&L cover
  futures and options, identically in live runs and replays.
- `participant-risk-limits`: sizing, risk floor, stop/flatten, and loss bounds
  account for notional leverage and short-premium risk.
- `trading-game-session`: margin-call/liquidation and closing-bell settlement
  cover derivatives (expiry, cash settlement, forced close).
- `brokerage`: fee schedules extend to per-contract derivatives commissions with
  the same deterministic per-execution deduction and visible totals.
- `order-ticket`: ticket entry, sizing, and preview cover futures margin and
  option premium plus buying-power guards.
- `terminal-trading-ui`: terminal panels display derivative series, premia/margin,
  and expiring positions; order entry and working-order management work for them.

## Impact

- Core: matching engine (new books or series-aware books), instrument master,
  event journal + replay, calibration harness (determinism proof for new books).
- Server: portfolio/risk/settlement pipelines, fee deduction, margin calls.
- Web: ticket, positions, history, chart/chain display for series and premia.
- Bots: market-maker liquidity for the new books; rival strategies optionally
  unchanged. Docs: `future-ideas.md` Tier 4 options item resolved by this change.
