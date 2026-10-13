# Design — Web DOM tests

## Context

See `proposal.md`. Vitest 5 is already the runner (`vitest run`); suites are
colocated `*.test.ts` (pure) and `*.dom.test.ts` (happy-dom pragma). Ticket,
panels, and portfolio mounts take explicit element/client/feed arguments, so
stubs substitute cleanly.

## Goals / Non-Goals

**Goals:** pragma-based happy-dom wiring, stub-mounted suites for ticket
modes/ladder, strip rows, and dock tabs.

**Non-Goals:** chart widget tests, e2e, shared test utilities beyond stubs.

## Decisions

### D1 — Per-file environment pragma

`// @vitest-environment happy-dom` atop each DOM suite instead of a
`vitest.config.ts`. No config file to maintain; pure suites stay on node.
- *Alternative considered:* central config — rejected; one line per file is
  cheaper than a config surface.

### D2 — Stub at the seam, assert on DOM

Clients/feeds stubbed as plain objects (`searchSymbols`, `banks`,
`onPayload`, `placeOrder`; feed `state`/`onChange`); assertions read rendered
DOM (buttons, rows, transmitted symbols). Timers: suites avoid waits except a
zero-delay flush for promise resolution; `destroy()` clears pending lock
timers.

## Risks / Trade-offs

- [happy-dom fidelity] Not a real browser (layout, focus) → suites assert
  structure and wiring, never pixels or focus rings.

## Migration Plan

Dev-only: nothing ships. Rollback = revert.
