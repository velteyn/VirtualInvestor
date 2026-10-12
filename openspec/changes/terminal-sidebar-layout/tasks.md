# Tasks — Terminal sidebar layout

Web-only, no server changes.

## 1. Sidebar grid without page scroll

- [x] 1.1 Restyle the grid (`"chart sidebar" / "dock sidebar"`, sidebar
  full-height flex, dock capped with internal scroll, chart keeps remainder),
  and verify `npm run build` green with no page-level scrollbar at desktop
  sizes.

## 2. News wire to the dock

- [x] 2.1 Move the news wire section from the sidebar into a dock News tab
  reusing its renderer, and verify headlines still stream (existing wire
  behavior, visual check).

## 3. Integration

- [x] 3.1 Run `npm test`, `npm run build` green and `openspec validate
  terminal-sidebar-layout --type change` valid.

## Workflow follow-up

- Archive after the queued edge change, then this one.
