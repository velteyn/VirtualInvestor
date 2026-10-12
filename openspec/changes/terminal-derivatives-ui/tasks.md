# Tasks — Terminal derivatives UI

Design has no open questions. Web-only, no server changes.

## 1. Shared position-view helpers

- [x] 1.1 Create `web/src/app/positions-view.ts` with row classification
  (shares/futures/calls/puts from the symbol), per-kind value/invested math,
  option split (intrinsic, time value, ITM/OTM, payoff-if-held), and expiry
  marking, and verify `npm test` covers every branch including the
  missing-mark fallback.

## 2. Portfolio sections

- [x] 2.1 Render Holdings as Shares/Futures/Calls/Puts sections (fixed order,
  empty sections hidden) with the spec'd per-section columns, and verify
  `npm test` covers sectioning (shares-only, mixed, empty) plus the futures
  variation-not-notional rule.
- [x] 2.2 Keep the unified account header (equity, cash, buying power, locked,
  realized, fees) summing rendered rows, and verify header-row agreement in
  `npm test`.

## 3. Strip consistency

- [x] 3.1 Migrate the positions strip and account line to the shared helpers
  (variation for futures, premium MTM for options, expiry marks, locked
  display), and verify `npm run build` plus `npm test` green.

## 4. Integration

- [x] 4.1 Run `npm run build` green and `openspec validate
  terminal-derivatives-ui --type change` valid.

## Workflow follow-up

- Archive `futures-options` first, then this change (shared requirements).
- Follow-up slice (separate change): product-aware ticket morph.
