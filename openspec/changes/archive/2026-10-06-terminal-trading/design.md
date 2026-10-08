# Design

## Context

See `proposal.md` for motivation and `specs/*/spec.md` for requirements. The `openalgo-charts`
widget (already mounted in `web/`) owns the chart, DOM ladder, drawing rail and — with its trade
tier — the working-order lines, position/P&L line and SL/TP brackets. The edge already has the
`WireMessage` command types, `RoundRuntimeService.submit` (mapped to core commands), and
`MarketBroadcaster`; the only missing server piece is the WebSocket dispatch. The runtime already
accepts commands, applies fills and streams events.

### D7. Order events are self-contained, FIX `ExecutionReport`-style

**Decision:** The core order lifecycle is a single `GameEvent.OrderReport` record carrying the
order's full identity on every report — `orderId`, `participantId`, `symbol`, `side`,
`orderType`, `quantity`, `price`, `filledQuantity`, `leavesQuantity`, `status` (`OrderStatus`),
`event` (`OrderEventKind`: NEW/RESTED/TRIGGERED/PARTIAL_FILL/FILL/CANCELLED/REJECTED) and
`reason` — rather than one thin record per lifecycle transition carrying only the field that
changed.

**Rationale:** This is how real order-management systems work. In FIX, every order update is an
`ExecutionReport(35=8)` which "confirms receipt... relays fill information on working orders",
and carries `Side(54)`, `OrdType(40)`, `Symbol(55)`, `OrderQty(38)`, `CumQty(14)`, `LeavesQty(151)`
and `OrdStatus(39)` on **every** report, including rejects (see Cboe, Crypto.com, Paxos FIX
docs). A consumer must be able to rebuild the working-order book from the stream alone. Our
fill-only records made that impossible and left the terminal's order frames with empty
side/type.

**Alternatives:** bolting `side`+`type` onto only the fill events (partial, still not
rebuildable); a runtime side-table looked up per order id (works but is not the model, and does
not survive pure event replay). Rejected in favour of the standard shape.

**Consequences:** the change touches the deterministic core (event records, codec, golden-master
hash) and several test fixtures. The events become the canonical order book replay, which also
strengthens `persistence-resume`.

## Goals / Non-Goals

**Goals:**

- A playable terminal: watch the live market and trade it.
- Close `market-microstructure` 6.2 by dispatching client orders over the socket.
- Reuse the widget's trade tier rather than rebuild order lines and brackets.

**Non-Goals:**

- New market mechanics; server behavior is fixed.
- Historical/imported data.
- A bespoke charting or order-line implementation.

## Decisions

### D1. Bind the widget to the live feeds; delete the seeded placeholder

**Decision:** `VirtualInvestorDataFeed.getBars` returns warm-up history fetched from the server
(a new `GET /api/rounds/{id}/history` or the round's stored history) and `subscribeBars`/
`subscribeDepth` drive live updates from the existing frames; the seeded generator is removed.

**Rationale:** A chart that ticks is the whole point; the placeholder was only to make the shell
render.

### D2. Mount the trade tier and route `onOrder` to the socket

**Decision:** Mount the widget trade tier overlays (via `chart.tradeHost`/`TradeController` as in
the library's phase-8/9 examples) and pass `onOrder` to `createWidget`; `onOrder` translates the
widget's `OrderRequest` into a `placeOrder` wire command and sends it on the socket.

**Rationale:** The widget already produces order intents from the menu and ticket; we only
transport them. The right-click and ticket share one path.

### D3. The socket dispatches inbound commands to the round

**Decision:** `GameWebSocketHandler.handleTextMessage` decodes order commands and calls
`RoundRuntimeService.submit(roundId, participantId, payload)` (participant = the round's human).
The round's resulting events are already broadcast by `RoundBroadcaster`, so they return to the
same socket.

**Rationale:** Reuses the tested service path and the existing broadcast; keeps the handler thin.

**Alternatives:** a new REST order endpoint (works, but the terminal already holds the socket and
the library's flows are stream-based).

### D4. Panels: widget-provided where possible, custom where not

**Decision:** News uses the widget's news panel + `NewsFeed`. Rivals, watchlist and positions are
custom panels fed by a leaderboard poll/stream, quote frames and account frames.

**Rationale:** Fewer bespoke widgets, and the widget's news panel already matches the design.

### D5. Layout matches the approved sketch

**Decision:** Chart fills the main area; a right column holds Rivals (top), Watchlist (middle),
News (bottom); an order ticket and a positions strip span the bottom.

**Rationale:** Approved by the user; standard trading-terminal arrangement.

### D6. Reconnect and reconcile

**Decision:** On socket reconnect, request a `resync` and re-fetch account/positions/book; the
widget's reconciliation and the `ReconnectService` already support this.

**Rationale:** A dropped socket must not desync the terminal from the authoritative server.

## Risks / Trade-offs

- **Library trade-tier API shape** → mounting `tradeHost`/`TradeController` may differ from the
  examples; mitigation: build against the phase-8/9 example code and its tests.
- **Widget `onOrder` vs the trade tier** → two order paths could diverge; mitigation: make both
  funnel through one adapter function.
- **Latency of order → fill → overlay** → the round applies commands at the next tick (≤1 tick);
  mitigation: optimistic working-order line then reconcile, as the library does.
- **Scope** → large; sequence so the wire dispatch and the live chart land before panel polish.

## Migration Plan

Additive in `web/` and `edge`. The seeded feed is removed once the live path works; rollback is
restoring it. Server changes are limited to the WS handler.

## Open Questions

- Whether history comes from a new history endpoint or the round's in-memory warm-up bars —
  decide during implementation; does not change the specs.
- Exact bracket handling (provider-linked vs two child orders) — start with two child orders and
  refine.
