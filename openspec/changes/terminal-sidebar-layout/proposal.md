# Proposal — Terminal sidebar layout (change: terminal-sidebar-layout)

## Why

Playtest: the right column is tied to the chart row's height and scrolls with
scrollbars, while the dock tabs and chart share the left. The request: a
full-height right shoulder (rivals, watchlist, action panel down to the
footer), chart + tabbed views on the left, and no page-level scrollbars.

## What Changes

- Grid: sidebar spans both rows (chart + dock height), full viewport height.
- Sidebar holds rivals, watchlist, and the ticket (action panel); the news
  wire moves out of the sidebar into the dock as a News tab (alongside
  Positions/Portfolio/company/newsroom).
- No page scrollbars: sidebar and dock scroll internally within capped
  heights; chart keeps full remaining space.
- **What is NOT in scope**: any new data or endpoints; ticket form changes;
  mobile breakpoint behavior stays as-is.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `terminal-trading-ui`: terminal layout (sidebar spans full height; news
  wire becomes a dock tab).

## Impact

- Web only: `styles.css` grid, `panels.ts` (sidebar vs dock split),
  `ticket.ts` mount point (unchanged behavior, follows the sidebar),
  dock tab wiring.
- No server changes.
