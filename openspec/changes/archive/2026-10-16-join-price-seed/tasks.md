# Tasks — Join price seed

## 1. Seed marks from history

- [x] 1.1 Ticket: seed underlying marks/touch from last close at mount and
  on symbol change (absent-only); DOM test: LIMIT prefills the close with no
  payloads.
- [x] 1.2 Panels: seed watchlist prices at mount (absent-only); DOM test:
  rows show seeded prices with no payloads.

## 2. Integration

- [x] 2.1 `npm test`, `npm run build` green, `openspec validate` green;
  live fresh-join check (price visible in <2s).
