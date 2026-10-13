# Tasks — Playtest fixes

## 1. Ticket + terminal (done, verified live)

- [x] 1.1 Chain fallback to substring search; PUT-leg + fallback DOM tests.
- [x] 1.2 `[hidden]` guard for ticket toggles; per-mode hidden-rows test.
- [x] 1.3 Strip P&L re-renders on trade prints; P&L-tick test.

## 2. Journal + newsroom (done, tested)

- [x] 2.1 `pruneTicksBelow`: checkpoints keep fills/reports/prints;
  `EventLogTest.pruneTicksBelowKeepsFills` (needs vi-db).
- [x] 2.2 Subject-less prompts name no companies; desk preview/recap
  try/catch + warn; prompt unit test.

## 3. Integration

- [x] 3.1 Web 55 green + build green; core/persist/narrator/edge suites
  green; `openspec validate` green. Live redeploy + playtest on green.
