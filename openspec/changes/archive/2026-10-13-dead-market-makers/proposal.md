# Proposal — Dead market makers (change: dead-market-makers)

## Why

Playtest (2026-10-13, room `round-1-d251`): a running round shows zero fills
for every participant, zero trade prints, empty previews, and orders that rest
forever. The edge log shows the maker series-quoting path throwing every few
seconds:

```
IllegalArgumentException: notional must be finite and non-negative
  at FeeSchedule$3.feeForSeries (feeForSeries)
  at RunningRound.creditFill (RunningRound.java:792)
  at RunningRound.applyFill ← step$3 (series onFill)
```

Mechanism: for far-OOM option rungs the ladder price rounds to zero or
negative; the quote rests, crosses another maker's quote, and the fill's
negative notional throws inside `creditFill`. The throw aborts `step()`, so
the tick's events (prints, reports, positions) are dropped while the engine
state already mutated — books drain, nothing is ever broadcast, the market
looks alive (ticks advance, charts show warm-up) but no trade can happen.

## What Changes

- `BotRunner.refreshQuote`: skip ladder rungs with non-finite or non-positive
  prices — makers never post invalid quotes (root cause).
- `MatchingEngine.match`: reject LIMIT orders with non-finite or non-positive
  limit prices (book integrity for every caller, bots included).
- `RunningRound.creditFill`: clamp the fee notional so fill accounting can
  never throw mid-fill and abort a tick (defense in depth).
- **NOT in scope**: spread model changes, fee schedule changes, tick-diff
  behavior for valid quotes.

## Capabilities

- `market-simulation` (delta: quote/order price validity + total fill
  accounting).

## Impact

- `server/core` (`MatchingEngine`, + unit tests), `server/runtime`
  (`BotRunner`, `RunningRound`, + regression test stepping a round with
  far-OOM series until fills print).
- Live redeploy of edge after merge (the playtest room needs a fresh round).
