# Tasks

## 1. Per-symbol realized (core)

- [x] 1.1 Track realized P&L per symbol in `Account` at the same site the lump sum moves; verify a fill-accounting test with a partial close booking half the gain.
- [x] 1.2 Carry per-symbol realized through position state and the purse write-back; verify the round-trip persistence test covers it.

## 2. Fill-history query (edge + persist)

- [x] 2.1 Fold the round journal's `OrderReport` fills per participant behind `POST /api/rounds/fills`; verify an API test where only the requester's fills return with price, quantity, fee and tick.
- [x] 2.2 Prove replay-equivalence: rebuilding history from a recovered journal matches the live run; verify against the recovery test fixture.

## 3. Portfolio tab (web)

- [x] 3.1 Build the tab: account header, holdings table with the specified columns, working orders with modify/cancel, history section; verify `npm run build`.
- [x] 3.2 Reconcile on mount and reconnect from resync frames plus the history query, replacing local state; verify a dropped-connection drill shows fills that completed while away.
- [x] 3.3 Style the tab in the terminal theme with a table for tabular data; verify on desktop and phone widths.

## 4. Verification and docs

- [x] 4.1 End-to-end over a live server: trade, close the browser, return after fills and closes, confirm positions, history and gains match the server; verify all steps in order.
- [x] 4.2 Document the tab and the resume guarantee in `docs/terminal.md`; verify the documented return-after-absence steps reproduce the full picture.
