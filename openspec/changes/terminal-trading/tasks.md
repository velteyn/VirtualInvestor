# Tasks

## 1. WebSocket order dispatch (closes market-microstructure 6.2)

- [ ] 1.1 Route inbound `placeOrder`/`modifyOrder`/`cancelOrder` in `GameWebSocketHandler` to `RoundRuntimeService` for the round's human participant; verify a WebSocket integration test that a sent command is queued on the round.
- [ ] 1.2 Stream the resulting order and fill events back to the sender through the existing broadcast; verify the sender receives its order events.

## 2. Live data feed in the terminal

- [ ] 2.1 Bind `VirtualInvestorDataFeed` to server history (warm-up bars) for `getBars`; verify the chart loads real history for a running round.
- [ ] 2.2 Drive live bar and depth updates from the stream; verify the chart and DOM ladder update as the market trades.
- [ ] 2.3 Remove the seeded placeholder feed and confirm the chart still renders with live data only.

## 3. Trade tier and order routing

- [ ] 3.1 Mount the widget trade tier (working-order lines, position/P&L line, SL/TP bracket) over our feeds; verify a working order and a position render from streamed frames.
- [ ] 3.2 Route the widget `onOrder` (menu and ticket) into a `placeOrder` wire command over the socket; verify an order placed in the terminal reaches the round and its line appears.
- [ ] 3.3 Route drag-to-modify and cancel into `modifyOrder`/`cancelOrder`; verify a dragged line settles at the server-accepted price and a cancelled order disappears.

## 4. Panels

- [ ] 4.1 Build the rivals leaderboard panel (ranked, equity + return, excludes institutions, updates live); verify it reorders as standings change.
- [ ] 4.2 Build the watchlist panel from quote/price frames; verify prices update live.
- [ ] 4.3 Wire the news panel to the `NewsFeed` over news frames; verify headlines appear as released with source and scope.
- [ ] 4.4 Build the positions strip with live unrealized P&L and a close action; verify P&L updates on a tick and closing flattens the position.

## 5. Layout and reconciliation

- [ ] 5.1 Assemble the approved layout (chart left; rivals/watchlist/news right; order ticket + positions bottom) and verify all panels are visible and populated.
- [ ] 5.2 Handle socket reconnect with a resync of orders, positions and account; verify the terminal reconciles after a dropped connection.

## 6. Verification and docs

- [ ] 6.1 End-to-end: start a round in the browser, watch the market tick, place a market order and a limit order, modify and cancel, and close a position — all reflected server-side.
- [ ] 6.2 Document the terminal UI and its wiring in `docs/terminal.md`; verify the documented run steps reproduce a tradeable terminal.
