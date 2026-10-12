# Design — Series chart history

## Context

See `proposal.md`. `WarmupGenerator.generateHistory` builds spot bars while
stepping the fundamental model; `RunningRound.start` merges the map into
`history` (which already feeds `barBaseSec` per symbol and the history
endpoint per symbol). Series books start empty and are maker-quoted live.

## Goals / Non-Goals

**Goals:** every listed series opens with the same bar count as its
underlying, seed-reproducible, served through the unchanged history path.

**Non-Goals:** overlays, watchlist, quote seeding for series books (live
makers cover it), any pricing change.

## Decisions

### D1 — Derive, never simulate

New pure function `WarmupGenerator.seriesHistory(universe, spotHistory,
yearsToBell)`: futures bars copy underlying OHLC (carry is zero intraday,
tick-snapped); option bars evaluate the premium formula at each OHLC —
calls map directly (premium rises with spot), puts invert high/low;
volume is 0 (no invented activity). No RNG touched: same seed always yields
the same series bars.
- *Alternative considered:* stepping a series fundamental — rejected; series
  have no fundamentals by design (they derive from underlyings).

### D2 — Merge at the existing join

`RunningRound.start` merges the derived map into `history`. `barBaseSec`,
the history endpoint, and live-bar continuation then cover series with zero
special cases (the per-symbol loops already key off the map/engines).

### D3 — Time input comes from the round

`yearsToBell` = remaining session length at warm-up
(`sessionTicks * stepMillis / YEAR_MILLIS`); vol = underlying
`baseVolatility`. Same inputs as live marks, so warm-up premia sit on the
same surface the maker quotes.

## Risks / Trade-offs

- [Zero-volume bars] Charts may render flat volume rows for history →
  truthful (no invented activity); live prints add real volume immediately.
- [Strike moneyness drift] Far strikes show near-zero premia all through
  warm-up → correct (they were far OTM the whole time).

## Migration Plan

Core-only, additive: new method + merged map entries. No stored state, no
wire changes. Rollback = revert.
