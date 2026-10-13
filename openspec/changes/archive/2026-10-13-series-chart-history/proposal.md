# Proposal — Series chart history (change: series-chart-history)

## Why

Series charts open blank: warm-up history generates for spot only, so futures
and options show nothing until live prints arrive (and far strikes stay empty
all session). A chart with no backstory is unreadable for timing entries.

## What Changes

- Warm-up generates synthetic history for listed series alongside spot:
  futures from underlying history (carry is zero intraday, so a level copy
  with the series' tick rounding), options from the deterministic premium
  formula evaluated over underlying history (same vol/time inputs as live
  marks, time-to-bell at warm-up start).
- The history endpoint serves series bars identically to spot; live bars
  continue the axis as today (series already anchor to the shared history
  end).
- Clearly derived data: series history SHALL be reproducible from seed alone
  (no new randomness), so replays stay exact.
- **What is NOT in scope**: underlying overlays on series charts; watchlist
  membership for series; any pricing or settlement change.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `trading-game-session`: round warm-up covers listed series with derived
  history.
- `terminal-trading-ui`: chart history loads for series symbols too.

## Impact

- Server: warm-up generation (+ tests); history endpoint unchanged in shape.
- Web: none (charts already render whatever history returns).
