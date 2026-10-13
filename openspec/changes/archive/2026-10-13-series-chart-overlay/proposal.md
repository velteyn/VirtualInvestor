# Proposal — Series chart overlay (change: series-chart-overlay)

## Why

A futures/options chart alone hides the relationship that defines the
product: futures trade against spot (basis), options against the underlying
that drives their premium. Seeing the underlying behind the series makes
leverage and moneyness readable at a glance.

## What Changes

- When the charted symbol is a derivative series, the terminal adds the
  underlying as a comparison line (library `addComparison` API) behind the
  series candles: history plus live bars, removed when leaving the series.
- Always on, no toggle in v1; subtle line style so series price action stays
  primary.
- **What is NOT in scope**: basis/premium spread panels, toggles, overlays
  for spot symbols, any server changes (both histories already stream).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `terminal-trading-ui`: charting a series overlays its underlying.

## Impact

- Web only: terminal chart wiring + data-feed overlay subscription.
- No server changes.
