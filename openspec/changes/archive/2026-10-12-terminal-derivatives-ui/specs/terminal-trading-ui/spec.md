# Spec Delta

## MODIFIED Requirements

### Requirement: Manage working orders and positions

The terminal SHALL let the player modify a working order by dragging its line,
cancel it, and close a position, and SHALL reflect the results from the
server's order and position streams. Derivative positions SHALL show expiry
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
