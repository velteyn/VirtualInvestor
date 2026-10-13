# Tasks — Series discovery

## 1. Server chain endpoint

- [x] 1.1 Expose `POST /api/symbols/chain` (`{underlying, preset}`) via
  `SymbolSearch.chain()` with `SymbolResponse` enrichment; core/edge tests.
- [x] 1.2 Client adapters (`searchSymbols` sibling `chain(underlying)`).

## 2. Ticket + watchlist

- [x] 2.1 Ticket loads the chain from the new endpoint; remove the
  `${ticker}-` search hack; ticket DOM suite updated.
- [x] 2.2 Searchable watchlist: query box + add/remove, `localStorage`
  persistence, series rows chartable; panels DOM suite covers it.

## 3. Integration

- [x] 3.1 `npm test`, `npm run build`, edge tests, `openspec validate`
  green.
