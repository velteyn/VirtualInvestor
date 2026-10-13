# Design — Playtest fixes

## Context

See `proposal.md`. Five small defects, one per layer; each fix is the cheapest
cut that closes its finding without changing economics or pacing.

## Decisions

### D1 — Chain fallback (ticket.ts)

`applyChain` extracted; `client.chain().then(applyChain, () => search-then-applyChain)`.
Failure mode unchanged (Shares-only when both fail). DOM tests: PUT leg,
fallback path, per-mode hidden rows.

### D2 — CSS guard + P&L tick (styles.css, panels.ts)

`.vi-ticket label[hidden], .vi-ticket-side[hidden], .vi-tk-ladder[hidden] {
display: none }` — same pattern as the existing `.vi-confirm[hidden]` guard.
`renderPositions()` also runs on trade payloads (subscription order safe: the
closure only fires post-mount). Dock tabs unaffected (`.vi-tab` sets no
display; verified live).

### D3 — Tick-only prune (persist)

New `JdbcEventLog.pruneTicksBelow(seq)` (`type = 'Tick'`); the checkpoint
calls it instead of `pruneBelow`. Ticks are pure replay scaffolding; fills,
reports and prints stay for history. Growth trade-off: the events table keeps
~200k rows/round (pre-prune status quo ante), bounded by round count, not
time — accepted for a LAN game; articles already accumulate the same way.

### D4 — Newsroom grounding + survival (narrator, edge)

`promptFor`: empty subjects → "Name no companies at all" instead of the
names rule with an empty set. `preview`/`recap` desk bodies get the same
try/catch+warn `write()` already has, so a failure logs instead of killing
the desk thread silently.

## Risks

- Persist tests need vi-db (compose); suite covers the new prune path.
- Narrator prompt change is prompt-only; sidecar behavior verified by unit
  test on the prompt text, not the model.
