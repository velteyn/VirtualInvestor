# Tasks — Web DOM tests

## 1. Environment

- [x] 1.1 Add `happy-dom` as a devDependency and wire the DOM environment
  for DOM suites, and verify a trivial mount test passes under `npm test`.

## 2. Mount suites

- [x] 2.1 Ticket mount: mode switch reshapes fields (ladder appears in
  Options, future line in Futures, plain form in Shares) with a stubbed
  chain, and strike selection sets the series for transmit.
- [x] 2.2 Panels strip + dock tabs: positions render from feed state and
  tab buttons switch views.

## 3. Integration

- [x] 3.1 Run `npm test`, `npm run build` green and `openspec validate
  web-dom-tests --type change` valid.
