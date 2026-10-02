# Market rules

This document describes the market rules the simulation enforces and how the realism preset
toggles them. It must be kept in step with `com.virtualinvestor.core.rules`. Presets are
selected on the launch pad (task 8.1).

## Always-on rules

These are enforced for every order regardless of preset:

- **Tick grid** — a limit or stop-limit price must be a multiple of the instrument's tick size
  (`OrderValidator`, `MarketRules.tickSize`). Market and stop orders carry no price and skip
  this check.
- **Quantity grid** — order quantity must be a multiple of the instrument's quantity grid.
- **Minimum quantity** — order quantity must be at least the instrument's minimum.
- **Positive quantity** — order quantity must be greater than zero.

Rejections carry an explicit reason string.

## Preset-toggled rules

| Rule | ARCADE | REALISTIC (default) | HARDCORE |
|---|---|---|---|
| Price bands / circuit breakers | off | on | on |
| Band fraction | 0.20 (unused) | 0.10 | 0.05 |
| Halt duration (ticks) | 0 | 50 | 100 |
| Short selling | unrestricted | shortable instruments only | shortable instruments only |
| Initial margin fraction | 0.25 | 0.50 | 0.50 |
| Maintenance margin fraction | 0.125 | 0.25 | 0.30 |

- **Price bands / circuit breakers** (`CircuitBreaker`): when the last traded price moves
  beyond the band from the reference, trading halts for the halt duration; the reference
  resets to the triggering price on resume.
- **Short selling** (`ShortSalePolicy`): a sell that would leave a participant net short is
  refused for a non-shortable instrument unless the preset allows unrestricted shorting.
  Sells that only reduce a long are always allowed.
- **Margin fractions** (`RealismPreset.initialMarginFraction`,
  `maintenanceMarginFraction`): defined per preset and consumed by margin calls and
  settlement (task 8.5). Until 8.5 lands, they are configuration only and are not yet
  enforced.

## Not yet implemented

- Margin calls and liquidation enforcement (task 8.5).
- Locate/borrow fees and financing for shorts (future).
- Opening/closing auction participation rules beyond the auction itself (task 5.4).
