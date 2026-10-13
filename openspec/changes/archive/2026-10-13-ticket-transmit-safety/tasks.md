# Tasks — Transmit safety

Web-only, no server changes.

## 1. Lock and dialog

- [x] 1.1 Lock Transmit from click to terminal report or 5s timeout, and
  verify `npm test` covers lock/unlock transitions on a tracked order
  (extract the tiny state machine pure).
- [x] 1.2 Add the threshold confirm dialog (`needsConfirm` pure: SIZE 25%,
  MARGIN legs, OFFMARKET 5%) showing economics + firing rules, and verify
  `npm test` covers every trigger and the one-click path below thresholds.

## 2. Integration

- [x] 2.1 Run `npm test`, `npm run build` green and `openspec validate
  ticket-transmit-safety --type change` valid.

## Workflow follow-up

- Archive `ticket-product-morph` first, then this one (shared requirement).
