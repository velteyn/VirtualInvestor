# Proposal

## Why

The market has prices but no companies: 21 tickers with names, sectors, and reference prices, and nowhere to read what any of them *are*. A Yahoo-finance-style company tab gives players a reason to care about names — due diligence before positions — and gives the future newsroom's stories subjects with identities.

## What Changes

- One profile per instrument (21): a short fictional description plus the static fundamentals already in `instruments.csv` (sector, reference price, betas, volatility, shares).
- A company tab in the terminal with a symbol selector, showing the profile and fundamentals for the chosen name.
- All content fictional and static: no runtime, no model, no engine changes. Profiles ship as a web asset; FictionalNames rules apply (no real companies).

## Capabilities

### New Capabilities

- `company-profiles`: static per-instrument profiles with a terminal tab to read them.

### Modified Capabilities

(none)

## Impact

- `web` only: new content asset + tab component following the portfolio-tab pattern; terminal wiring for the tab slot.
- `server`: untouched (no endpoint — content is static and ships with the client).
- `docs/`: profile-writing conventions (fictional, consistent with sectors and reference prices).
