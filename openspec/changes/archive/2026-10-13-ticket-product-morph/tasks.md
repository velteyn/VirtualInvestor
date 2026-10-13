# Tasks — Product-aware ticket

Web-only, no server changes.

## 1. Mode switch

- [x] 1.1 Add the Shares/Futures/Options mode switch reshaping fields
  (futures: single future line + leverage readout + bell badge; options:
  call/put toggle + strike ladder with live premia; shares: unchanged), and
  verify `npm test` covers the pure ladder/mode helpers.
- [x] 1.2 Keep transmit values, guards, and reasons identical across modes,
  and verify `npm test` plus existing preview-guard suites still pass.

## 2. Integration

- [x] 2.1 Run `npm test`, `npm run build` green and `openspec validate
  ticket-product-morph --type change` valid.
