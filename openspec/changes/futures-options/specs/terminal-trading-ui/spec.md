# Spec Delta

## MODIFIED Requirements

### Requirement: Order entry from the terminal

The player SHALL place orders from the terminal — market or limit, buy or sell,
with a quantity — through a dedicated order ticket and the chart's order menu,
and SHALL be able to attach a stop-loss and take-profit bracket. The terminal
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

The terminal SHALL let the player modify a working order by dragging its line,
cancel it, and close a position, and SHALL reflect the results from the
server's order and position streams. Derivative positions SHALL show expiry
marking and live mark-to-market or premium value, and SHALL close through the
same action with settlement results reflected from the streams.

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

### Requirement: Live account and P&L

The terminal SHALL show the player's cash, buying power and open positions with
live unrealized P&L, updating from the account and position streams. The
display SHALL break out locked margin, futures variation, and option market
value so levered P&L is explainable at a glance.

#### Scenario: P&L updates on a tick

- **WHEN** the price of a held instrument moves
- **THEN** the position's unrealized P&L updates in the terminal

#### Scenario: Margin breakout visible

- **WHEN** the player holds margined derivatives
- **THEN** the account shows locked margin alongside free cash and buying power
