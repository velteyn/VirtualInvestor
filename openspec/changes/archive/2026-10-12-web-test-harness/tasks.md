# Tasks — Web test harness

## 1. Runner

- [x] 1.1 Add Vitest as a devDependency with a `test` script in
  `web/package.json`, and verify `npm test -- --run` executes (no tests yet).
- [x] 1.2 Add ticket helper suites (`previewQuote`, `parseSeriesSymbol`,
  `previewSeries` incl. every transmit-disable reason, `maxAffordableSeries`,
  `rememberedQty`, `touchPriceFor`), and verify all pass.
- [x] 1.3 Run `npm run build` green and `openspec validate web-test-harness
  --type change` valid.

## Workflow follow-up

- Follow-up change (needs `happy-dom`): DOM tests for `mountTicket`/panels.
