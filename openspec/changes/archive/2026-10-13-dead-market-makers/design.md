# Design — Dead market makers

## Context

See `proposal.md`. Three layers, cheapest first: makers stop posting garbage,
the book refuses garbage from anyone, accounting survives garbage anyway.

## Decisions

### D1 — Skip invalid rungs in BotRunner.refreshQuote

In the desired-ladder build, `continue` (leaving `null`, i.e. withdrawn) when
`!Double.isFinite(price) || price <= 0`. Slot diffing already treats `null`
as withdrawn, so no other change needed. Unit test: reference 0.01 with the
default 0.02 spread posts no bid rungs.

### D2 — Reject in MatchingEngine.match

Before matching: `incoming.type() == LIMIT && (!isFinite(limitPrice) ||
limitPrice <= 0)` → `MatchResult` rejected with reason (same shape as the
quantity guard at line 20). MARKET/STOP unaffected (no limit price).

### D3 — Clamp in RunningRound.creditFill

`feeForSeries(quantity, max(0, finite-or-0(notional)))`. Position/realized math
uses the raw fill price (unchanged economics for valid fills; invalid fills
cannot occur after D1+D2, the clamp is pure survival).

## Risks

- Bot quote tests may assert exact rung counts for low references → update to
  the skip behavior; suite will show.
