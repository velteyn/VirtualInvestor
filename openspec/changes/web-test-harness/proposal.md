# Proposal — Web test harness

## Why

The web terminal has no test runner: task 7.2's ticket-guard verification could
only ride on `tsc` + server-side tests. Every preview/fee/guard formula in the
terminal is duplicated client logic that can drift from the server unnoticed.

## What Changes

- Add Vitest (Vite-native, no build changes) with a `test` script in
  `web/package.json`.
- Add `*.test.ts` suites for the pure, DOM-free helpers: ticket preview and
  series guards (`previewQuote`, `previewSeries`, `maxAffordableSeries`,
  `parseSeriesSymbol`), sizing memory, and touch pricing.
- DOM mounting tests (`mountTicket`, panels) stay out: they need a DOM
  environment (`happy-dom`) and are an explicit follow-up, not this change.

## Capabilities

None — tooling only, no spec-level behavior changes. `skip_specs: true`.

## Impact

- `web/package.json` (+ devDependency, `test` script), `web/src/**/*.test.ts`.
- `npm run build` untouched; CI (if any) gains `npm test --workspace` or the
  web-local equivalent.
