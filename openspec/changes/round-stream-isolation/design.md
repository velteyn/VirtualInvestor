# Design — Round stream isolation

## Context

See `proposal.md`. One shared `MarketBroadcaster` (global session set, depth
coalescing keyed by symbol) + one shared `RoundBroadcaster` singleton
(`bindEngines` keeps the last round's engines) + global newsroom broadcasts.

## Goals / Non-Goals

**Goals:** per-round fan-out; per-round depth coalescing; per-round bridge
engines; newsroom bodies addressed to their round.

**Non-Goals:** client changes; auth; lobby; per-participant filtering
(participant scoping already exists client-side).

## Decisions

### D1 — Sessions keyed by round in the broadcaster

`MarketBroadcaster` holds `Map<roundId, Set<session>>`: `join(session,
roundId)` moves the socket (rebind safe), `leave(session)` removes it
everywhere, `broadcast(roundId, payload)` / `depth(roundId, dto)` fan out
only within the round. Unbound sockets (connected, never joined) receive
nothing. Depth coalescing keys become `roundId|symbol`.
- *Alternative considered:* tagging every payload with roundId and filtering
  client-side — rejected; it still ships other games' data down every socket.

### D2 — Per-round bridge instances

`RoundRuntimeService` builds `new RoundBroadcaster(broadcaster, roundId)` per
round instead of injecting the shared singleton. The class drops
`@Component`. `bindEngines` then always points at its own round.
- Existing `new RoundBroadcaster(broadcaster)` call sites (tests) gain the
  roundId argument.

### D3 — Newsroom threads roundId through

Both broadcast sites in `NewsroomService` already sit in methods that know
the roundId — pass it to `broadcast`. No signature changes elsewhere.

## Risks / Trade-offs

- [Socket joins two rounds] Last join wins (one binding per socket) → matches
  the client's single-terminal-at-a-time model.
- [Depth flush on rebind] A rebinding socket may miss a coalesced depth that
  was queued for the other round → harmless: depth replays every window and
  resync replays state.

## Migration Plan

Edge-only, no stored state, no wire shape changes: restart the server.
Rollback = revert.
