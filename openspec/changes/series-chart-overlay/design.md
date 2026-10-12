# Design — Series chart overlay

## Context

See `proposal.md`. The openalgo-charts library offers `addComparison(chart,
{symbol, bars})` with `handle.setBars` / `handle.remove`; our
`VirtualInvestorDataFeed` already serves history + live bars per symbol.
Series symbols encode their underlying (`parseSeriesSymbol` exists in the
ticket module; classification also lives in `positions-view.ts`).

## Goals / Non-Goals

**Goals:** underlying line behind any charted series, history + live, cleaned
up on symbol switch.

**Non-Goals:** toggles, basis panels, spot overlays, server work.

## Decisions

### D1 — Host-driven comparison, feed-backed data

`terminal.ts` owns one `ComparisonHandle | null`: on chart symbol change, if
the symbol parses as a series, fetch underlying history (`dataFeed.getBars`),
`addComparison` as a subtle line, subscribe live underlying bars and push via
`setBars`; on any symbol change, remove the previous handle first. Reuses the
feed's existing per-symbol caching — no new endpoints.
- *Alternative considered:* a second widget pane — rejected; heavier, and the
  comparison aligns scales/time for free.

### D2 — Shared symbol helper

Move series parsing for chart use to `positions-view.ts` (`underlyingOf`
already exists unexported there — export it) rather than importing from
`ticket.ts`, keeping chart code free of ticket concerns.

## Risks / Trade-offs

- [Widget API surface] `addComparison` import comes from the main
  `openalgo-charts` entry, not `/widget` → verify the import resolves at
  build; fallback is a vendored line primitive (much more work).
- [Stale overlay] Live underlying bars must replace, not append →
  `setBars` with the full refreshed array each update (small arrays, cheap).

## Migration Plan

Web-only: refresh (dev) or rebuild. Rollback = revert.
