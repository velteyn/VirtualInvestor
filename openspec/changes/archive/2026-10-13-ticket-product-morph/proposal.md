# Proposal — Product-aware ticket (change: ticket-product-morph)

## Why

Playtest: one generic form serves three different products. An option buyer
sees the same fields as a share buyer — no strike context, no moneyness, a
15-item dropdown of bare tickers. The ticket should teach what each product
is while keeping the single transmit path.

## What Changes

- Ticket gains a product mode switch: Shares / Futures / Options.
- Futures mode: underlying picker, side/qty/limit as today, plus a leverage
  readout (notional vs locked margin) and an expiry badge (bell).
- Options mode: call/put toggle plus a strike ladder showing live premia per
  strike (from marks, formula fallback), then side/qty with premium preview.
- Shares mode: exactly today's ticket, untouched.
- Same transmit path, same guards with the same reasons; presentation only.
- **What is NOT in scope**: new order types, pricing or fee changes, engine
  work.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `order-ticket`: product-mode form (mode switch, per-kind fields, strike
  ladder with live premia).

## Impact

- Web only: `ticket.ts` (+ tests for the new pure helpers).
- No server changes.
