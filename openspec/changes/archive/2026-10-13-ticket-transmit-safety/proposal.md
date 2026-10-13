# Proposal — Transmit safety (change: ticket-transmit-safety)

## Why

Every Transmit click mints a fresh client token, so server idempotency cannot
see double-clicks: a rapid double press sends two orders and fills twice.
Preview and server guards validate economics, never intent.

## What Changes

- **In-flight lock (always on):** Transmit disables from click until that
  order reaches a terminal report (FILLED/CANCELLED/REJECTED) or a 5s timeout,
  then re-enables. Second presses land on a dead button.
- **Confirm dialog (threshold-gated):** orders above risk thresholds show a
  review dialog (side, qty, price, notional, fee, margin lock, buying power
  after, and the reason it asked) requiring explicit confirm; small routine
  tickets stay one-click. Triggers: notional > 25% of buying power, any
  margined leg (futures/shorts), or limit price > 5% off touch (fat-finger).
- Fixed thresholds in v1 (no settings page); the dialog states which rule
  fired.
- **What is NOT in scope**: server changes (idempotency already exists);
  order types; configurable thresholds.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `order-ticket`: transmit lock during flight; confirm dialog with triggers;
  fat-finger rule.

## Impact

- Web only: `ticket.ts` (+ tests for the pure trigger/preview helpers).
- No server changes.
