# terminal-trading-ui Specification

## Purpose
Defines the browser trading terminal: its layout and panels, how each panel is fed from the live stream, and how the player submits, modifies and cancels orders from it.

## Requirements

### Requirement: Live terminal layout

The terminal SHALL present a single screen: on the left, a candlestick chart
(carrying the market overlays) above a tabbed dock (Positions, Portfolio,
company, News) — the wire and its full-story articles share the single News
tab, with no separate newsroom tab; on the right, a full-height sidebar holding
the rivals leaderboard, the watchlist, and the order ticket down to the
footer. The page SHALL NOT scroll: the sidebar and the dock scroll internally
within the viewport height.

#### Scenario: Terminal opens on a running round

- **WHEN** the player starts a round from the launch pad
- **THEN** the terminal opens with the chart, the rivals, watchlist and news panels, the order ticket and the positions strip visible

#### Scenario: Panels are populated

- **WHEN** the round is running
- **THEN** the chart shows live bars, the rivals show live standings, the watchlist shows live prices, and the news panel shows wire headlines

#### Scenario: Sidebar spans full height

- **WHEN** the terminal is open on a wide screen
- **THEN** rivals, watchlist and ticket stack in a right sidebar reaching the
  footer, and no page-level scrollbar appears

#### Scenario: News wire lives in the dock

- **WHEN** the player opens the dock tabs
- **THEN** a News tab shows the same wire headlines previously stacked in the
  sidebar

#### Scenario: One news tab, not two

- **WHEN** the player opens the dock tabs
- **THEN** exactly one News tab appears, and full stories open from its rows

### Requirement: Chart is fed by the live stream

The chart SHALL load history from the server and update from the live stream — bars, trades and depth — rather than a seeded placeholder, and SHALL draw the working-order and position overlays. History loads SHALL work for
derivative series symbols exactly as for spot. When the charted symbol is a
derivative series, the chart SHALL additionally draw its underlying as a
comparison line (history plus live bars) behind the series, removed when the
chart leaves the series.

#### Scenario: Chart ticks

- **WHEN** the server publishes a new bar or trade
- **THEN** the chart updates without a reload

#### Scenario: Depth drives the ladder

- **WHEN** the server publishes depth for the chart's instrument
- **THEN** the DOM ladder shows the current bid and ask levels and sizes

#### Scenario: Series chart opens with history

- **WHEN** the player charts a listed future or option before it trades
- **THEN** warm-up bars render instead of an empty chart

#### Scenario: Underlying overlays its series

- **WHEN** the player charts a listed series
- **THEN** the underlying draws as a comparison line behind the series and
  tracks live bars until the chart switches symbol

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

The player SHALL place orders from the terminal — market or limit, buy or sell, with a quantity — through a dedicated order ticket and the chart's order menu, and SHALL be able to attach a stop-loss and take-profit bracket. The terminal
SHALL additionally offer futures and option series selection with premium and
margin previewed in the ticket, without bracket attachment on derivative legs.

#### Scenario: Place a market order

- **WHEN** the player submits a buy market order for a quantity
- **THEN** the order is sent to the server and its working order appears on the chart

#### Scenario: Place a limit order from the menu

- **WHEN** the player uses the chart's order menu to place a limit order at a price
- **THEN** the order is sent and a draggable order line appears at that price

#### Scenario: Invalid order is rejected

- **WHEN** the player submits an order the server refuses (off-tick price, halted instrument, insufficient buying power)
- **THEN** the terminal reports the reason and shows no working order

#### Scenario: Derivative order placed from terminal

- **WHEN** the player selects a futures or option series and confirms the ticket
- **THEN** the order is submitted with that series symbol and shown values

### Requirement: Manage working orders and positions

The terminal SHALL let the player modify a working order by dragging its line, cancel it, and close a position, and SHALL reflect the results from the server's order and position streams. Derivative positions SHALL show expiry
marking and live mark-to-market or premium value, and SHALL close through the
same action with settlement results reflected from the streams. Position rows
SHALL use kind-appropriate values everywhere they appear: variation for
futures, premium MTM for options, and an expiry mark for all series.

#### Scenario: Drag to modify

- **WHEN** the player drags a working order line to a new price
- **THEN** the order is modified on the server and the line settles at its accepted price

#### Scenario: Close a position

- **WHEN** the player closes a position
- **THEN** the server flattens it and the position and P&L update from the stream

#### Scenario: Expiring position visible and closable

- **WHEN** the player holds a series expiring at the bell
- **THEN** the position shows its expiry and MTM or premium, and closing it
  reflects the server result

#### Scenario: Futures row shows variation, not notional

- **WHEN** the player holds a future with entry far below the mark
- **THEN** every position display shows the MTM variation, never the leveraged
  notional value

### Requirement: Orders travel over the WebSocket

The terminal SHALL submit `placeOrder`, `modifyOrder` and `cancelOrder` commands over the WebSocket, and the server SHALL route them to the running round and stream the resulting order and fill events back to subscribers.

#### Scenario: Command round-trips

- **WHEN** the terminal sends a `placeOrder` command over the socket
- **THEN** the server queues it on the running round and the resulting order events are streamed back

#### Scenario: Repeated command is idempotent

- **WHEN** the terminal retries a command with the same client token
- **THEN** the server applies it once

### Requirement: Live account and P&L

The terminal SHALL show the player's cash, buying power and open positions with live unrealized P&L, updating from the account and position streams. The
display SHALL break out locked margin, futures variation, and option market
value so levered P&L is explainable at a glance.

#### Scenario: P&L updates on a tick

- **WHEN** the price of a held instrument moves
- **THEN** the position's unrealized P&L updates in the terminal

#### Scenario: Margin breakout visible

- **WHEN** the player holds margined derivatives
- **THEN** the account shows locked margin alongside free cash and buying power
