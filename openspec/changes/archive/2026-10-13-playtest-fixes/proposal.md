# Proposal — Playtest fixes (change: playtest-fixes)

## Why

First full playtest as a registered player (room, trade shares/futures/options,
news, portfolio, bell) surfaced four defects beyond `dead-market-makers`,
each verified live in headless Chromium against the playtest server:

1. Ticket chain loader hard-depends on `/api/symbols/chain`: against any
   server predating the endpoint the ladder stays empty and puts/calls are
   unselectable. (This broke the live game when series-discovery shipped.)
2. Mode tabs leaked controls: CSS `display` rules beat the `hidden`
   attribute, so Futures rendered the Series dropdown plus Call/Put.
3. Positions strip P&L frozen between fills (spec violation:
   terminal-trading-ui requires P&L updates on a tick).
4. Fill history evaporates: checkpoint `pruneBelow` deletes every event below
   the snapshot, so `/fills` reads back empty after each checkpoint and the
   settled round keeps one `SessionClosed` row.
5. Newsroom desk can die silently (`preview`/`recap` have no try/catch, unlike
   `write`), and subject-less prompts tell the model to "use ONLY these
   fictional company names" while naming none — the sidecar then invents
   companies (observed: "TechPrime", "GreenGlobe").

## What Changes

- Ticket: `chain()` with substring-search fallback; degrades instead of
  stranding (1).
- Styles: `[hidden]` guard for the ticket's toggled rows (2); P&L re-renders
  on trade prints (3). Both align code to existing spec text, no deltas.
- Persist: checkpoints prune only `Tick` scaffolding; fills/reports/prints
  survive for history and review (4).
- Narrator: subject-less prompts say "name no companies" (5a); desk
  preview/recap log instead of dying silent (5b).
- **NOT in scope**: generation throughput (single desk × ~50s/ea), last-price
  snapshot on join (dark ticket for seconds on quiet tapes), bot activity
  density — all reported, none blocking.

## Capabilities

- `order-ticket` (delta: chain fallback), `portfolio` (delta: history spans
  checkpoints), `newsroom-narrator` (delta: no invented names).

## Impact

- `web/src/app/ticket.ts`, `styles.css`, `panels.ts` (+ DOM suites, 55 green).
- `server/persist` (`JdbcEventLog`, `RoundRecoveryService`, + test),
  `server/narrator` (prompt + test), `server/edge` (`NewsroomService`).
