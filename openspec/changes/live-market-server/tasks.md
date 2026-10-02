# Tasks

## 1. World runtime scaffolding

- [x] 1.1 Add a `runtime` package/module and a `RunningRound` composer that wires `WorldClock`, `InstrumentUniverse`, `FundamentalModel`, `VolatilityState`, per-instrument `InstrumentEngine`s, bots, `SessionStateMachine`, `MarginManager` and `Settlement`; verify a unit test constructs a running round from a seed + config.
- [x] 1.2 Add a single-producer command queue the loop drains at the top of each tick; verify a test that enqueues commands and observes them applied in order.
- [x] 1.3 Implement the fixed-step tick that advances fundamentals, volatility, news, bots, session phase and margin in a documented order; verify a test that a fixed seed advances deterministically.

## 2. Warm-up and session behavior

- [x] 2.1 Implement start: `CREATED → WARMUP → RUNNING`, generating history and seeding books via `WarmupGenerator`; verify a round is running with non-empty books and history.
- [x] 2.2 Gate trading by session phase (auctions at boundaries, continuous only in `CONTINUOUS`); verify a timeline test that orders outside continuous are refused.
- [x] 2.3 Implement settlement at the close (`Settlement.settle`, emit `RoundSettled`, stop the loop); verify a settled round refuses further orders.

## 3. Order intake

- [x] 3.1 Add client order-command messages to `WireMessage` and map them to core `Command`s at the edge; verify a codec/mapping test for place/modify/cancel.
- [x] 3.2 Validate and match commands through `TradeGateway`, returning explicit rejection reasons; verify tests for tick, quantity, short-sale, halt and price-band rejections.
- [x] 3.3 Enforce idempotency by client token; verify a repeated command creates one order.
- [x] 3.4 Apply every fill to both counterparties' `Account` before emitting events; verify an account/ledger test for a matched order.

## 4. Persistence wiring

- [ ] 4.1 Append the loop's events through `BatchedEventLogWriter` without blocking the tick; verify events persist in order across many ticks.
- [ ] 4.2 Checkpoint via `RoundRecoveryService.checkpoint` on a cadence; verify a snapshot exists and recovery from it equals a full replay (golden-master hash).

## 5. Streaming

- [ ] 5.1 Map the loop's core events to wire payloads and hand them to `MarketBroadcaster`; verify a subscriber receives trade, bar, news and order frames.
- [ ] 5.2 Keep depth coalescing and ensure a slow/dead session does not stall the loop; verify a test with a failing session.
- [ ] 5.3 Stream sequence/tick on events and support catch-up from a subscriber's last sequence; verify a reconnect test that returns only missed events.

## 6. Lifecycle and recovery

- [ ] 6.1 Wire round control: create starts warm-up + loop; pause/resume act on the running world; verify each end to end.
- [ ] 6.2 On startup, recover a round from latest snapshot + events and resume paused or running; verify a simulated restart mid-round with no lost or duplicated fill.

## 7. Verification and docs

- [ ] 7.1 Run a bot-only round on the server and verify prices move, books update, news fires and the leaderboard settles (headless integration test).
- [ ] 7.2 Run a session where a client submits orders over the wire and verify fills, positions, account and settlement reflect them.
- [ ] 7.3 Document the server runtime (loop, intake, streaming, recovery) in `docs/server-runtime.md`; verify the documented run commands start a live round.
