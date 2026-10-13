# Design — Series discovery

## Context

`SymbolSearch` already has both operations (`search`, `chain`); only `search`
is exposed (`RoundController:74`). The ticket abuses search for chain loading;
the watchlist is a hardcoded constant.

## Decisions

### D1 — Chain endpoint mirrors search enrichment

`POST /api/symbols/chain` takes `{underlying, preset}`, calls
`GameService.chain()` → `SymbolSearch.chain()`, maps through the same
`SymbolResponse` builder as search (extract shared mapper). No ranking, no
limit — a chain is 15 rows today (1 future + 7 calls + 7 puts).

### D2 — Client `chain()` alongside `searchSymbols`

`ServerClient.chain(underlying, preset)` POSTs the new path; `DataFeed` not
touched (chart data path unchanged). Ticket swaps `loadChain` body to it;
failure mode unchanged (Shares-only picker).

### D3 — Watchlist: search box, add/remove, localStorage

Panels gains a `<input type=search>` + results dropdown backed by
`client.searchSymbols`. Rows keyed by symbol, `×` removes, `+`/Enter adds the
top match. Stored under `vi.watchlist.v1` as `string[]`; invalid entries
dropped on load. Series rows reuse `onSelectSymbol` — charting already
supports any symbol with bars.

## Risks

- Chain/history mismatch: series without bars won't chart — mitigated: all
  listed series have synthetic warm-up history (series-chart-history).
