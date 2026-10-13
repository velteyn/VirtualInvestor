# Design — Transmit safety

## Context

See `proposal.md`. Today `send` click handler: validate → guard check →
`rememberQty` → register token → `placeOrder`. Own-order reports arrive on
the socket stream (`ownOrders` set, terminal statuses remove). Preview math
(`previewQuote`/`previewSeries`, `funds`, touch) already computes everything
a dialog must show.

## Goals / Non-Goals

**Goals:** no duplicate transmits; threshold dialog with stated reasons;
fat-finger rule; same guards/reasons.

**Non-Goals:** server work; settings UI; order types.

## Decisions

### D1 — Lock keyed by client token with timeout

On Transmit: disable `send`, start a 5s timer. Re-enable on the order's
terminal report (already tracked in `ownOrders`) or timer expiry, whichever
first. Timer handle cleared on terminal report to avoid late re-enables
racing the next ticket.
- *Alternative considered:* debounce clicks (ignore presses <300ms apart) —
  rejected; it masks the intent problem and still double-sends across the
  window.

### D2 — Dialog is a blocking review, thresholds as pure helpers

`needsConfirm({notional, buyingPower, margined, offTouchFraction})` returns
the firing rule names (pure, unit-tested): `SIZE` over 25% BP, `MARGIN` for
any margined leg, `OFFMARKET` over 5% off touch. The dialog renders
side/qty/price/notional/fee/lock/left + reasons, Confirm re-enters the
existing transmit path (which then locks per D1), Cancel returns focus.
Escape cancels.

## Risks / Trade-offs

- [Timeout vs slow fill] A fill slower than 5s re-enables while working →
  acceptable: re-press transmits a *new* order intentionally; the lock's job
  is the accidental double, not session state.
- [Fixed thresholds] May annoy on big-account routine tickets → values chosen
  to fire rarely; configurable later (explicit non-goal).

## Migration Plan

Web-only: refresh (dev) or rebuild. Rollback = revert.
