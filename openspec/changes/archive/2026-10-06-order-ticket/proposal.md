# Proposal

## Why

Order size is hardcoded to 100 shares: the chart menu fires blind market/limit
orders with no preview, no fee estimate and no validation message. A ticket
with explicit sizing, cost preview and transmit is the missing operating half
of the terminal — and the place where players learn what trading costs.

## What Changes

- A ticket panel in the right column below news: side toggle, type selector
  (MARKET/LIMIT/STOP/STOP_LIMIT), quantity with remembered default plus lot
  buttons and Max, limit/stop prices prefilled from the touch and following it
  until edited, fee preview with buying-power guard, Transmit.
- Chart `onOrder` (menu, Buy/Sell buttons) stops transmitting and prefills the
  ticket instead; drag-to-modify of working lines is untouched. The ticket is
  the single transmit path.
- Rejection reasons surface inline on the ticket (threaded through the order
  frame), keeping typed values so a fix is one edit away.
- Always-preview in v1 (no one-click mode); plain orders only (no brackets —
  the wire has no linked-order semantics yet, that is a later server change).

## Capabilities

### New Capabilities

- `order-ticket`: ticket entry, sizing memory and lots, touch-prefill pricing,
  fee preview with buying-power guard, inline rejection reasons, and
  prefill-instead-of-transmit chart behavior.

### Modified Capabilities

None — wire, matching and resync are untouched (one additive `reason` field on
the order frame, which is implementation detail under the new capability).

## Impact

- Web only: new ticket component in the right column, `onOrder` rewired to
  prefill, small styles; `OrderDto.reason` populated at the existing
  broadcaster mapping.
- Docs: ticket run-through in `docs/terminal.md`.
