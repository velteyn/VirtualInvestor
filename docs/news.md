# News and events

The news wire is the heartbeat of a round. It moves fundamental values, raises volatility, and
gives the rivals something to react to. This document describes the implemented model in
`com.virtualinvestor.core.news`.

## Sources (all fictional)

`NewsSource` has three fictional outlets: **THE_WIRE**, **MERIDIAN_NEWS**, **TAPE**.
`NewsSource.fromToken` refuses any other name, and `FictionalNames` rejects real company or
outlet names in headlines and bodies, so a real name can never enter the market.

## Taxonomy

`NewsScope` classifies an event by what it moves:

| Scope | Impact (`FundamentalModel.applyNews`) |
|---|---|
| `COMPANY` | the named instrument only |
| `SECTOR` | every instrument in the named sector |
| `MACRO` | every instrument, scaled by its market beta |
| `STRUCTURE` | the named symbol (reserved for market-structure events) |

Each item carries a `direction` (-1, 0, 1), a non-negative `magnitude`, and a `durationTicks`
describing whether its effect is a pop or a drift. The signed impact is
`direction * magnitude`.

## Generation

`NewsGenerator` builds a round's timeline from the round seed:

- one scheduled **earnings** event per equity (scope `COMPANY`, stage `CONFIRMED`) at a fixed
  fraction of the session that spreads across names;
- random events whose count is `sessionTicks / ticksPerRandomEvent`, choosing a scope by a
  weighted roll (company-heavy, then sector, then macro), a direction, a magnitude, and a
  duration.

All draws come from the `news.random` stream, so the same seed yields identical events at
identical ticks. The event count scales with session length.

## Staging: rumor, confirm, deny

`PlannedNews` stages a story over time and `NewsEngine` applies it:

1. at the rumor tick, the fundamental moves by `signedMagnitude * rumorFraction` and a
   `NewsReleased` event with stage `RUMOR` is emitted;
2. at the resolution tick, a confirmed story applies the remaining
   `signedMagnitude * (1 - rumorFraction)` and emits `NewsConfirmed`; a denied story reverses
   the rumor move and emits `NewsDenied`.

## Impact on volatility

`VolatilityState` holds a per-instrument multiplier (1.0 = calm). When significant news is
applied, `NewsEngine` raises the multiplier for every affected instrument by the magnitude of
the move; the multiplier decays each step. `FundamentalModel` scales idiosyncratic diffusion
by the multiplier, so realized volatility rises after a shock, and `MarketMakerStrategy` quotes
a wider spread as the volatility input rises.

## Events emitted

- `NewsReleased` (stage `RUMOR`)
- `NewsConfirmed`
- `NewsDenied`

## Not yet implemented

- Human-facing delivery cadence and fairness (spec: fair human cadence) — arrives with the
  edge/terminal wiring.
- `STRUCTURE`-scope generators (halt/squeeze/index-rebalance events).
- Scheduled non-earnings events (macro calendar) known in advance.
