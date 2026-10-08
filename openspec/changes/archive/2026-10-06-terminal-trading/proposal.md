# Proposal

## Why

The server runs a live, order-driven, risk-limited market and streams it, but the browser
terminal is read-only scaffolding: its chart shows seeded history, its panels are unbound, and
it cannot place an order. There is also no way for a client to submit an order over the wire —
the WebSocket handler acknowledges nothing to the world. Without this, a player can watch the
market but not trade it.

## What Changes

- Wire the terminal's **data feed to the live stream** so the chart ticks: bars, depth, trades
  and news from the server replace the seeded placeholder, and the order/position/account
  streams drive the trade overlays.
- Implement **WebSocket order dispatch**: inbound `placeOrder`/`modifyOrder`/`cancelOrder`
  commands are routed to the running round and their results stream back, closing the gap left
  open by `market-microstructure`.
- Build the **trading terminal UI**: a chart with the market overlays, a **rivals leaderboard**
  panel, a **watchlist** panel, a **news (the wire)** panel, an **order ticket** (buy/sell,
  quantity, type, price, bracket SL/TP), and a **positions** strip with live P&L.
- Route the widget's **`onOrder` callback** (right-click order menu and ticket) into the wire
  command path, and reconcile the resulting order/position/account frames into the overlays.

Explicit non-goals for this change:

- No new market mechanics; the server's impact and risk behavior is used as-is.
- No mobile-specific redesign beyond what the widget already does.
- No imported/historical data (the `historical-replay` change owns that).

## Capabilities

### New Capabilities

- `terminal-trading-ui`: The browser trading terminal — the layout and panels (chart with
  overlays, rivals, watchlist, news, order ticket, positions), how each is fed, and how the
  player submits and manages orders from it.

### Modified Capabilities

- `market-streaming`: Adds the **inbound command** direction — the server SHALL accept
  `placeOrder`/`modifyOrder`/`cancelOrder` over the WebSocket, route them to the running round,
  and stream the resulting order and fill events back to subscribers.

## Impact

- **Web**: `web/src/adapters/*` bind to the live stream (bars/depth/trades/news/orders/account);
  new panels (rivals, watchlist, news, positions) and the order-ticket wiring; the widget's
  `onOrder`/trade tier is mounted.
- **Edge**: `GameWebSocketHandler` dispatches inbound order commands to `RoundRuntimeService`
  instead of rejecting them; the wire set already has the command messages.
- **Runtime**: unchanged; it already accepts commands, applies fills, and streams events.
- **Closes**: `market-microstructure` task 6.2 (client order over the wire), which was blocked
  precisely on this dispatch.
