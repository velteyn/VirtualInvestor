# Proposal — Terminal derivatives UI (change: terminal-derivatives-ui)

## Why

The `futures-options` change trades three different products through one
shared ticket and one shared positions table. Playtesting shows the surfaces
teach nothing about what each product is: an option buyer sees the same form
as a share buyer, with no strike context, moneyness, or expiry payoff. Wall
$treet Trader 2001 solved this with product sections in one PDA view (shares,
futures, calls, puts side by side under a unified cash/net-worth header).

## What Changes

- **Portfolio becomes one tab with product sections**: a unified account
  header (equity, cash, buying power, locked, realized, fees — unchanged),
  then Shares / Futures / Calls / Puts sections, each with product-appropriate
  columns. A section renders only when it holds rows.
- **Section columns**:
  - Shares: today's table, unchanged.
  - Futures: side, entry, mark, MTM variation, locked margin, expiry, close.
  - Calls/Puts: strike, premium paid/received, mark, intrinsic vs time-value
    split, ITM/OTM badge, payoff-if-held-to-the-bell at current price, expiry,
    close.
- **Positions strip** (terminal bottom) gains the same per-row treatment in
  miniature: expiry mark (already there) plus kind-appropriate value
  (variation for futures, premium MTM for options — already there) — kept
  consistent, not redesigned.
- **What is NOT in scope**: the product-aware ticket morph (separate later
  slice); any matching-engine, journal, or settlement change; new order types.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `portfolio`: holdings table becomes sectioned by product with per-section
  columns; account header gains no new fields (locked/buying-power already
  added by `futures-options`).
- `terminal-trading-ui`: positions strip and account display follow the same
  per-product presentation.

## Impact

- Web only: `portfolio.ts`, `panels.ts` (presentation logic, no new endpoints —
  all data already streams).
- No server changes. No wire changes.
