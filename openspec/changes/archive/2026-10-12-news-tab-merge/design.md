# Design — News tab merge

## Context

See `proposal.md`. Today: `newswire.ts` (wire rows with badges) and
`newsroom.ts` (article archive with history load) are separate dock tabs
sharing the tab pattern; both key updates by news id.

## Goals / Non-Goals

**Goals:** one News tab; rows with bodies expand to full story in a popup;
history archive loads into the same tab for late joiners.

**Non-Goals:** generation, wire, feed semantics; visual redesign beyond the
merge.

## Decisions

### D1 — Expand the wire, retire the archive tab

`newswire.ts` gains: per-row "full story" affordance (only when
`body.length > 80`, the existing threshold) opening a popup overlay with the
complete text; initial history load from the articles endpoint (moved from
`newsroom.ts`); `newsroom.ts` tab mount removed (its fetch/render helpers
fold into the wire). Popup over inline-expand: long LLM bodies would push
wire rows off-screen; the popup preserves tape position.

### D2 — One updater, two sources

Live payloads upsert by news id (as today); history load fills on mount only
and never duplicates live rows (id-keyed merge, existing convention).

## Risks / Trade-offs

- [Popup vs terminal grid] A fixed overlay must sit above the chart widget →
  Mitigation: high z-index + click-outside/Escape to close, same pattern as
  chart menus.
- [Archive overlap] Same three changes touch `terminal-trading-ui` (sidebar
  layout) → archive that change first.

## Migration Plan

Web-only: refresh (dev) or rebuild. Rollback = revert.
