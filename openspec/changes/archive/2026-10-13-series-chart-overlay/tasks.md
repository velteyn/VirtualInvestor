# Tasks — Series chart overlay

Web-only, no server changes.

## 1. Underlying comparison line

- [x] 1.1 Export `underlyingOf` from `positions-view.ts` (with `npm test`
  cover for spot/series/invalid inputs).
- [x] 1.2 In `terminal.ts`, add/remove the underlying comparison around chart
  symbol changes (history + live `setBars`, cleanup on switch), and verify
  `npm run build` green plus a live visual check.

## 2. Integration

- [x] 2.1 Run `npm test`, `npm run build` green and `openspec validate
  series-chart-overlay --type change` valid.
