# Proposal — Web DOM tests (change: web-dom-tests)

## Why

The Vitest harness covers pure helpers only; mounting (`mountTicket`,
panels strip, dock tabs) is untested, so DOM wiring regressions (missing
elements, dead buttons, broken tab switches) reach playtesters.

## What Changes

- Add `happy-dom` as a devDependency for DOM-level suites.
- Mount tests: ticket mode switch reshapes the form; ladder renders from an
  injected chain and selecting a strike sets the series; panels strip renders
  positions from feed state; dock tabs switch views. Server/client boundaries
  stubbed (no sockets, no timers beyond the suite).
- **What is NOT in scope**: chart widget tests (third-party canvas), e2e.

## Capabilities

None — tooling only, no spec-level behavior changes. `skip_specs: true`.

## Impact

- `web/package.json` (+ devDependency), `web/src/**/*.dom.test.ts`
  (or environment pragma in colocated suites).
- `npm run build` untouched.
