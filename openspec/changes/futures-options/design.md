# Design — Futures + Options

## Context

See `proposal.md` for motivation; see `specs/` for the behavior contract.
The current state shaping this design (verified in code):

- One `InstrumentEngine` (book + stop book + last price) per symbol
  (`core/engine/InstrumentEngine.java`); matching, lifecycle reports, trade
  prints, depth and bars all key off the symbol string.
- `Instrument` is a record with `InstrumentType {EQUITY, INDEX}`
  (`core/instrument/`); the universe is built at round creation.
- `Account.applyFill` exchanges full notional cash and tracks avgPrice/realized
  per symbol (`core/participant/Account.java`) — correct for equities and,
  as shown below, for option premia, but wrong for futures (no notional
  exchange at entry).
- `MarginManager`/`Settlement` already mark, margin-call, force-close and rank
  (`core/game/`); settlement is pure intraday — everything is flat after the
  bell today.
- Events are a closed sealed `GameEvent` set; replay rebuilds the world from
  seed + log (`core/event/GameEvent.java`).

## Goals / Non-Goals

**Goals:** series trade on the existing intake/matching/journal rails with zero
new order types; premia and settlement stay pure functions of (seed, log,
underlying state) so replays stay exact; every new number is explainable in
the terminal (the WST lesson — see `docs/wst2001-inspiration.md`).

**Non-Goals:** no American exercise, no multi-expiry chains, no Greeks display,
no overnight carry, no rival-bot derivatives strategies, no real-company names.

## Decisions

### D1 — Series are instruments, each with its own book

Add `FUTURE` and `OPTION` to `InstrumentType`; list series in
`InstrumentUniverse` at round creation (deterministic from seed + underlying
reference prices); spawn one `InstrumentEngine` per series symbol.

- *Why:* every downstream system (matching, stops, lifecycle events, depth,
  bars, halts, journal) already keys on symbol. A series-aware mega-book would
  fork all of them.
- *Alternative considered:* embedding series inside the underlying's book —
  rejected; it tangles price-time priority across different payoffs.
- *Cost:* ~hundreds of mostly-empty books per round (strikes x 2 x
  underlyings + futures). Books are cheap when idle; market-making quotes only
  the liquid subset (D4).

### D2 — Futures track spot via market-maker basis, not a formula price

The future trades only by book matching. Institutional market-making quotes it
around `spot + deterministic carry(spot, time-to-bell)`, mirroring how quoting
already anchors on the fundamental model. MTM mark = series last traded price.

- *Alternative considered:* a synthetic mark (spot + carry) even with no
  trades — rejected; it would let MTM drift from executable prices and confuse
  the terminal. Empty series simply show the reference quote without a mark.

### D3 — Option premia are a closed-form function of observable state

Premium = Black-Scholes-style closed form of (underlying price, strike,
time-to-bell, configured per-underlying volatility from `baseVolatility`).
No vol surface, no calibration step, no RNG: same inputs give the same premium
in live runs and replays. The market-maker quotes around the formula value so
traded premia stay near it; the account mark for an option is last traded
premium, falling back to the formula when untraded.

- *Why:* an empty book must still show an explainable price, and replay
  exactness forbids any stateful pricer.

### D4 — Liquidity: institutions quote futures + near-money options only

`MarketMakerStrategy` extends to derivative books but only for futures and
options within one strike step of the reference price; far strikes are
formula-priced on demand and fillable when a player crosses them. Rival bots
keep equities-only strategies — identical rules, narrower choice, which the
participant model already permits.

- *Why:* quoting hundreds of series per tick would dominate the world budget
  for books nobody touches.

### D5 — Account gains margin accounting; options reuse `applyFill` semantics

- *Options:* premium settlement maps exactly onto `applyFill` (buyer BUY moves
  cash `-premium`, seller SELL moves `+premium`; avgPrice = premium; realized
  on reduce/flip). No new cash path needed.
- *Futures:* new path — entry locks `initialMargin` per contract into a new
  `lockedMargin` field (no notional cash exchange), position tracks entry
  price, MTM flows through `unrealized()` off the series mark. `equity()` and
  `buyingPower()` become `cash + derivatives value` and `cash - lockedMargin`
  respectively. Close/settle releases margin and books
  `(exit - entry) x multiplier x signedQty` into realized + per-symbol totals
  at the existing attribution site, so lump sum and per-symbol can never
  diverge.
- *Alternative considered:* moving MTM variation in cash between counterparties
  each mark — rejected; bilateral clearing against bots/institutions adds a
  whole settlement layer for no gameplay gain. Variation lives in equity until
  close/settle, exactly like equity unrealized P&L today.

### D6 — Risk and settlement extend, order fixed

`MarketRules` gains per-series margin/tick/grid fields; `MarginManager.check`
values positions on notional (futures) and covered exposure (short options);
`forceClose` closes series at mark/premium. Settlement order is deterministic:
closing-auction prices → futures cash-settle → options auto-exercise/expire →
equity mark + forced closes → ranking. Two new `GameEvent` records
(`FuturesSettled`, `OptionSettled`, each carrying series, quantity,
settlement price) make expiry replayable and feed the history query; halt
propagation is a universe-level flag (underlying halt ⇒ series halt).

## Risks / Trade-offs

- [Notional leverage vs loss bounds] → margins default conservative relative
  to the risk floor; floor-flatten and maintenance calls jointly enforce the
  bound; calibration asserts worst-case final equity per the risk spec.
- [Formula premium vs traded premium divergence] → mark prefers last trade,
  falls back to formula; terminal shows which one (explainability rule).
- [Book count growth] → D4 quoting subset; benchmarks extended to N-book rounds.
- [Short-option tail risk into the bell] → shorts are margin-covered at all
  times; exercise is automatic so no pin-risk decisions are needed.
- [Event-log growth from MTM] → marks are derived, never journaled; only
  fills, settlements, calls and closes append events.

## Migration Plan

Additive only: new enum values, new journal events (old logs contain none, so
old replays are unaffected), new account fields defaulting to zero. No stored
state crosses versions (intraday rounds). Rollback = stop listing series via
launch config; equities-only rounds are the degenerate case and must keep
passing the full suite.

## Open Questions

- Exact strike-step width and count per underlying (default: ATM ± 3 steps) —
  tunable in config, no spec impact.
- Whether the index gets futures/options in v1 or equities only — default yes
  for futures, no for options; flipping it changes listing, not contracts.
