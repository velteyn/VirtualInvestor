# Proposal

## Why

The market core, persistence and edge exist, but **nothing runs**: there is no server-side loop
ticking the world, so the market is static and orders cannot be placed or filled. Until the
server runs a round, the terminal has no live data to show and no book to trade against.

## What Changes

- Introduce a **world loop**: a running round's clock that advances the fundamental model, the
  news engine, the rival/institution bots, market rules (halts, margin) and session phases,
  and appends every world-affecting event to the durable log as it runs.
- Introduce **order intake**: accept `place`, `modify` and `cancel` commands from clients and
  bots, validate them (tick/quantity/short/band), match them against the book, apply fills to
  accounts, and emit the order lifecycle and trade events.
- Introduce **market streaming**: broadcast bars, trades, depth, order/position/account
  updates and news to WebSocket subscribers as the loop produces them.
- Wire the world loop to round control: **create** starts a warm-up then the loop; **pause**,
  **resume** and **settle** act on the running world; the loop is recoverable from the event log.

Explicit non-goals for this change:

- No terminal/UI work. Mounting the trade tier, the right-click `onOrder` wiring and order
  panels is a separate, later change (the interface).
- No new market model: the core's matching, fundamentals, news and bots are used as-is.
- No options/futures/CFDs, no persistent always-on world.

## Capabilities

### New Capabilities

- `world-loop`: The server-side running round — start/warm-up, the fixed-step tick that drives
  fundamentals, news, bots and market rules, session phases, margin/settlement, and appending
  the produced events to the event log; pause/resume/recover.
- `order-intake`: Accepting, validating and matching `place`/`modify`/`cancel` commands from
  any participant, applying fills to accounts, and emitting order-lifecycle and trade events.
- `market-streaming`: Pushing bars, trades, depth, order/position/account updates and news to
  WebSocket subscribers as the world produces them, with the coalescing and ordering guarantees
  the terminal relies on.

### Modified Capabilities

- None. This change builds on the framework-free core and the existing edge without changing
  their externally visible behavior; new behavior is captured in the new capabilities above.

## Impact

- **New server code**: a world runtime in the `game` module (or a new `runtime` module) that
  composes the existing core (`WorldClock`, `FundamentalModel`, `InstrumentEngine`,
  `NewsEngine`, bots, `SessionStateMachine`, `MarginManager`, `Settlement`), plus order-intake
  handlers at the edge and streaming publishers over the existing `/ws`.
- **Persistence**: the running loop appends events through the existing `BatchedEventLogWriter`
  and checkpoints via `RoundRecoveryService`, so a restart resumes the world.
- **Wire protocol**: adds client order-command messages and their acknowledgements to the
  existing `WireMessage` set; the streaming payloads already exist.
- **Concurrency**: the loop is single-threaded and deterministic; the edge marshals commands
  onto it and broadcasts its output.
- **Deferred**: the terminal trade tier (`OrderEngine`/`TradeController`/`onOrder`), which is
  the separate interface change.
