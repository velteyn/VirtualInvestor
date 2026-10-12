# Tasks — Round stream isolation

## 1. Per-round fan-out

- [x] 1.1 Key `MarketBroadcaster` sessions and depth coalescing by round
  (`join`/`leave`/`broadcast(roundId, …)`/`depth(roundId, …)`; unbound sockets
  get nothing), and verify `MarketBroadcasterTest` covers isolation, rebind,
  and per-round coalescing.
- [x] 1.2 Make `RoundBroadcaster` per-round (roundId ctor arg, drop
  `@Component`, wire per-round instances in `RoundRuntimeService`), and verify
  existing bridge tests pass with the new signatures.
- [x] 1.3 Thread roundId through both `NewsroomService` broadcasts, bind the
  socket to its round on join (unbind on close) in `GameWebSocketHandler`,
  and verify an isolation test: two rounds, one socket each, no cross-talk.
- [x] 1.4 Run the full server suite green and `openspec validate
  round-stream-isolation --type change` valid.

## Workflow follow-up

- Archive after the two queued changes (shared area: edge), then this one.
- Restart the live server to pick it up.
