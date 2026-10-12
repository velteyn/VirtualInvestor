# Design — Terminal sidebar layout

## Context

See `proposal.md`. Today: grid areas `"chart column" / "positions positions"`;
the column (rivals, watchlist, news wire) spans only the chart row and scrolls
as one block; the ticket mounts inside the column; the dock (tabs) spans full
width at the bottom. `mountPanels` builds column + strip + news wire together.

## Goals / Non-Goals

**Goals:** full-height sidebar (rivals/watchlist/ticket); news wire as a dock
tab; zero page scrollbars on desktop.

**Non-Goals:** new data/endpoints; mobile breakpoint (unchanged, stacks);
ticket form changes.

## Decisions

### D1 — Grid areas become `"chart sidebar" / "dock sidebar"`

Sidebar spans both rows. Sidebar is `display:flex; flex-direction:column;
height:100%; overflow:hidden`: rivals and watchlist flex-none with capped
max-heights and internal scroll; ticket flexes (`flex:1; min-height:0`) with
its own internal scroll as last resort. Dock gets `max-height:42vh` +
internal scroll; chart keeps `min-height:0` and all remaining space.

### D2 — News wire moves, logic untouched

The news section markup + subscription move from `mountPanels` into a
`mountNewsWire(dock)` tab beside portfolio/company/newsroom, reusing the same
row renderer. Panels keep rivals/watchlist/strip only.

### D3 — Ticket follows the sidebar

`mountTicket` keeps its signature; `terminal.ts` passes the sidebar column
element (same as today — the column IS the sidebar now). No ticket changes.

## Risks / Trade-offs

- [Small viewports] Full-height sidebar + 42vh dock can squeeze the chart →
  Mitigation: existing mobile breakpoint already stacks everything; desktop
  min assumed ≥700px height.
- [Ticket internal scroll] Very short windows still scroll inside the ticket →
  last resort only, same as today but bounded.

## Migration Plan

Web-only CSS/DOM reshuffle, no state: refresh to pick up (dev) or rebuild.
Rollback = revert.
