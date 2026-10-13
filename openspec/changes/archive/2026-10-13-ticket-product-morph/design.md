# Design — Product-aware ticket

## Context

See `proposal.md`. Today: one form (symbol + series dropdowns, side, type,
qty, limit/stop) with a preview line that already branches per kind
(`previewSeries`, `seriesMeta`, `funds` in `ticket.ts`); chain data arrives
via `searchSymbols` with margin fractions and reference prices.

## Goals / Non-Goals

**Goals:** mode switch reshaping fields/labels/preview; strike ladder with
live premia; same transmit path and guard reasons.

**Non-Goals:** order types, pricing, fees, engine.

## Decisions

### D1 — Mode is a view switch, not three tickets

One `mode: 'SHARES' | 'FUTURES' | 'OPTIONS'` state toggles field visibility
and preview text; `read()` keeps transmitting the effective symbol + shown
values. Futures mode hides the multi-series dropdown in favor of a single
future line (one future per underlying) with leverage + bell badge; Options
mode replaces the dropdown with call/put toggle + strike ladder.
- *Alternative considered:* three separate components — rejected; shared
  validation/preview/transmit logic would triple.

### D2 — Ladder rows from chain + marks

Each strike row: strike, premium = live mark if the series printed else the
listed reference, moneyness via `positions-view` math against the underlying
mark. Selecting a row sets the series (same value the dropdown used).
Pure `ladderRows(chain, marks)` helper, unit-tested.

## Risks / Trade-offs

- [Stale premia] Marks lag on quiet series → rows show reference-flavored
  premia; acceptable, they match the old dropdown hints and live prices win
  on touch.

## Migration Plan

Web-only: refresh (dev) or rebuild. Rollback = revert.
