# Proposal — Round stream isolation (change: round-stream-isolation)

## Why

Playtest: starting a new game shows news from other games. Root cause:
`MarketBroadcaster` fans every round's frames out to every connected socket —
streams are not scoped to the joined round. The same defect leaks other
rounds' trades/positions/orders and misroutes depth snapshots (the shared
`RoundBroadcaster.bindEngines` keeps only the last round's engines).

## What Changes

- Fan-out keyed by round: a socket receives only frames for the round it
  joined (rebind on re-join, unbind on close).
- One `RoundBroadcaster` per round (carries its roundId) instead of a shared
  singleton; depth coalescing scoped per round+symbol.
- Newsroom article bodies addressed to their round.
- **What is NOT in scope**: client changes (fresh mounts per game are already
  clean once the server scopes); auth/permissions; lobby listings.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `market-streaming`: frames are delivered only to subscribers of the
  producing round.

## Impact

- `edge`: `MarketBroadcaster`, `RoundBroadcaster`, `RoundRuntimeService`,
  `GameWebSocketHandler`, `NewsroomService`.
- Tests: broadcaster/handler round-scoping tests; full suite must stay green.
