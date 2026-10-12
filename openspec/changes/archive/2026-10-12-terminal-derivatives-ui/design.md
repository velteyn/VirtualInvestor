# Design — Terminal derivatives UI

## Context

See `proposal.md` for motivation. Current state (all streaming already):
- `portfolio.ts` renders one holdings table from `tradeFeed` positions +
  a local marks map (last-trade prices), plus a unified header.
- `panels.ts` renders the bottom positions strip + account line from the same feed.
- Positions carry `netQty/avgPrice/realized/locked`; account carries
  `cash/realized/fees/lockedMargin` (both added by `futures-options`).
- No strike/intrinsic stream exists; option math must derive client-side from
  series symbols (`STIKE` parse, already in `ticket.ts`) and underlying marks.

## Goals / Non-Goals

**Goals:** one tab, four conditional sections with product-true columns; strip
rows consistent with portfolio rows; zero new endpoints, zero wire changes.

**Non-Goals:** ticket morph (later slice); strike/expiry selectors; any number
the server doesn't already stream (no countdown clock — expiry renders as a
static "bell" label).

## Decisions

### D1 — Section by parsing the symbol, not by new data

`parseSeriesSymbol` (already in `ticket.ts`, move to a shared module or
duplicate the 10-line regex) classifies each row: no match → Shares;
`-F` → Futures; `-C`/`-P` → Calls/Puts. Sections render in fixed order
Shares, Futures, Calls, Puts and skip when empty.
- *Alternative considered:* a `kind` field on position frames — rejected;
  it would churn the event, codec, wire DTO, and TS feed for information the
  symbol already encodes.

### D2 — All option math is derived client-side

Strike from the symbol; underlying mark from the existing marks map;
intrinsic = max(±(underlying − strike), 0); time value = max(premium mark −
intrinsic, 0); ITM/OTM = intrinsic > 0; payoff-if-held = intrinsic at the
current underlying price. Missing underlying mark → fall back to avgPrice
(same fallback the strip already uses for `last`).
- *Why:* no new streams; the numbers are pure functions of already-rendered
  state, so they cannot disagree with the tape.

### D3 — Futures rows show variation as value, locked as invested

Matches the server's equity semantics (`Account.equity` counts only variation
for futures). Shares/options rows keep today's notional math. Header math
follows: totals sum per-row value/invested as rendered, so header and rows
can never disagree.

### D4 — Strip mirrors, portfolio explains

The strip keeps one-line rows (symbol + • exp, qty, variation/premium P&L,
locked where > 0, close) — glanceable. The portfolio sections carry the full
columns. Both read the same helpers so values match by construction.

## Risks / Trade-offs

- [Fallback marks] Underlying with no trade yet → intrinsic computed off
  avgPrice; rows show a slightly stale split until the underlying prints →
  acceptable: same fallback already governs `last` everywhere.
- [Two stock UIs] Strip + portfolio duplicate row logic → Mitigation: one
  shared `positions-view.ts` helper module for classification + per-kind math,
  imported by both.
- [Spec overlap] Same requirements touched by `futures-options` →
  archive that change first (already planned).

## Migration Plan

Web-only, additive rendering: ship behind no flag; rollback = revert. No
migration (no stored state).
