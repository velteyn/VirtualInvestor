# Proposal — Series discovery (change: series-discovery)

## Why

Derivatives exist but are hard to find. The watchlist is five hardcoded
equities (`panels.ts:16`), the chart can only switch via those rows, and the
ticket loads its chain through a search hack (`searchSymbols(`${ticker}-`)`,
`ticket.ts:482`) that depends on substring matching and an arbitrary limit.
No search box exists anywhere in the terminal.

## What Changes

- Server: dedicated `POST /api/symbols/chain` returning the full listed
  chain for one underlying via `SymbolSearch.chain()` (future first, calls
  then puts by strike), with the same `SymbolResponse` enrichment as search.
- Ticket: load the chain from the new endpoint; drop the `${ticker}-` hack.
- Terminal: searchable watchlist — a query box backed by `/symbols/search`,
  add/remove rows, selection still switches the chart. Watchlist persists in
  `localStorage` per browser; defaults stay the current five. Series symbols
  are addable (they already have bars/history) and chartable.
- **NOT in scope**: multi-expiry chains, quote panels, ranking changes to
  `SymbolSearch.search`.

## Capabilities

- `terminal-trading-ui` (delta: discovery), `order-ticket` (delta: chain
  source). Server chain endpoint is the mechanism; behavior lands in those two.

## Impact

- `server/edge` (`RoundController`, `GameService`), `web/src/adapters/*`,
  `web/src/app/panels.ts`, `web/src/app/ticket.ts`, DOM suites.
