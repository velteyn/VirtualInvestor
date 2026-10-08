# terminal-trading-ui Specification

## Purpose
Defines the browser trading terminal: its layout and panels, how each panel is fed from the live stream, and how the player submits, modifies and cancels orders from it.

## Requirements

### Requirement: Live terminal layout

The terminal SHALL present a single screen with a candlestick chart (carrying the market overlays) on the left, a right column stacked with the rivals leaderboard, watchlist and news panels, and an order ticket and positions strip along the bottom.

#### Scenario: Terminal opens on a running round

- **WHEN** the player starts a round from the launch pad
- **THEN** the terminal opens with the chart, the rivals, watchlist and news panels, the order ticket and the positions strip visible

#### Scenario: Panels are populated

- **WHEN** the round is running
- **THEN** the chart shows live bars, the rivals show live standings, the watchlist shows live prices, and the news panel shows wire headlines

### Requirement: Chart is fed by the live stream

The chart SHALL load history from the server and update from the live stream — bars, trades and depth — rather than a seeded placeholder, and SHALL draw the working-order and position overlays.

#### Scenario: Chart ticks

- **WHEN** the server publishes a new bar or trade
- **THEN** the chart updates without a reload

#### Scenario: Depth drives the ladder

- **WHEN** the server publishes depth for the chart's instrument
- **THEN** the DOM ladder shows the current bid and ask levels and sizes

### Requirement: News wire panel

The terminal SHALL include a news panel that lists incoming wire items with their source, scope and headline, newest first, and SHALL update as items are released.

#### Scenario: Headline appears

- **WHEN** the server releases a news item
- **THEN** it appears at the top of the news panel with its source and scope

### Requirement: Rivals leaderboard panel

The terminal SHALL show the rivals leaderboard as a ranked list of participants with current equity and return, updating as the round runs and excluding institutions.

#### Scenario: Standing changes

- **WHEN** a participant's equity changes
- **THEN** the leaderboard reflects the new order and figures

### Requirement: Order entry from the terminal

The player SHALL place orders from the terminal — market or limit, buy or sell, with a quantity — through a dedicated order ticket and the chart's order menu, and SHALL be able to attach a stop-loss and take-profit bracket.

#### Scenario: Place a market order

- **WHEN** the player submits a buy market order for a quantity
- **THEN** the order is sent to the server and its working order appears on the chart

#### Scenario: Place a limit order from the menu

- **WHEN** the player uses the chart's order menu to place a limit order at a price
- **THEN** the order is sent and a draggable order line appears at that price

#### Scenario: Invalid order is rejected

- **WHEN** the player submits an order the server refuses (off-tick price, halted instrument, insufficient buying power)
- **THEN** the terminal reports the reason and shows no working order

### Requirement: Manage working orders and positions

The terminal SHALL let the player modify a working order by dragging its line, cancel it, and close a position, and SHALL reflect the results from the server's order and position streams.

#### Scenario: Drag to modify

- **WHEN** the player drags a working order line to a new price
- **THEN** the order is modified on the server and the line settles at its accepted price

#### Scenario: Close a position

- **WHEN** the player closes a position
- **THEN** the server flattens it and the position and P&L update from the stream

### Requirement: Orders travel over the WebSocket

The terminal SHALL submit `placeOrder`, `modifyOrder` and `cancelOrder` commands over the WebSocket, and the server SHALL route them to the running round and stream the resulting order and fill events back to subscribers.

#### Scenario: Command round-trips

- **WHEN** the terminal sends a `placeOrder` command over the socket
- **THEN** the server queues it on the running round and the resulting order events are streamed back

#### Scenario: Repeated command is idempotent

- **WHEN** the terminal retries a command with the same client token
- **THEN** the server applies it once

### Requirement: Live account and P&L

The terminal SHALL show the player's cash, buying power and open positions with live unrealized P&L, updating from the account and position streams.

#### Scenario: P&L updates on a tick

- **WHEN** the price of a held instrument moves
- **THEN** the position's unrealized P&L updates in the terminal
