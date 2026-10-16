# Proposal — Join price seed (change: join-price-seed)

## Why

Playtest measurement: a freshly joined terminal shows "—" on every watchlist
row, an empty ticket preview, and a LIMIT field prefilled `0` for 6-12s,
because marks/touch fill only from live prints. In that window the ticket
transmits blind and the fat-finger guard (needs touch > 0) cannot fire. The
server already holds warm-up history per symbol in memory behind a fast REST
endpoint — the client just never asks for it outside the chart.

## What Changes

- Ticket mount + underlying change: fetch last close via `getHistory` and
  seed `marks`/`touch` (bid = ask = last close) when no live data exists yet;
  first real print/depth overwrites. Refresh preview/ladder after seeding.
- Panels mount: same seeding for the watchlist symbols so rows show prices
  instantly.
- Seed-only-if-absent: live data always wins; failures fall back to today's
  behavior (dark until first print).
- **NOT in scope**: server snapshot endpoint, denser bot prints, touching the
  wire protocol.

## Capabilities

- `terminal-trading-ui` (delta: instant prices on join).

## Impact

- `web/src/app/ticket.ts`, `web/src/app/panels.ts` (+ DOM suites).
- No server change. `npm test` + `npm run build` green, live join check.
