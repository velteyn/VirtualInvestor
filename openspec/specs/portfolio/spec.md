# portfolio Specification

## Purpose
The resume-grade portfolio: holdings with gains, working orders, account
totals, and the player's fill history — all backed by server state so closing
the browser loses nothing.

## Requirements

### Requirement: Holdings table

The system SHALL show one row per open position with symbol, signed quantity,
average price, last price, market value, invested amount, unrealized gain in
value and percent, and a close action.

#### Scenario: Holdings derive from streamed state

- **WHEN** the player holds 50 NVDO bought at 640 with last price 645
- **THEN** the row shows qty +50, avg 640, value 32250, invested 32000, unrealized +250 (+0.8%)

#### Scenario: Close flattens from the row

- **WHEN** the player closes a row
- **THEN** an offsetting market order is sent and the row disappears once flat

### Requirement: Account header

The system SHALL show equity with return on starting capital, cash, buying
power, unrealized P&L, realized P&L and total fees paid.

#### Scenario: Header reflects the account

- **WHEN** the account holds cash plus open positions
- **THEN** equity equals cash plus market value and return equals equity over starting capital

### Requirement: Per-symbol realized gains

The system SHALL track realized P&L per symbol as positions reduce or flip,
identically in live runs and event-log replays, and SHALL expose it on
position state.

#### Scenario: Closing half books half the gain

- **WHEN** half of a winning position is sold
- **THEN** half the unrealized gain moves to that symbol's realized total

### Requirement: Fill history query

The system SHALL provide the player's fills for the round — order id, symbol,
side, quantity, price, fee and tick — folded from the round journal, so the
tab's History section shows what executed while the player was away.

#### Scenario: History covers the absence

- **WHEN** a fill completes while the player is disconnected
- **THEN** the History section lists it after reconnect with price, quantity and fee

#### Scenario: History is participant-scoped

- **WHEN** the history is queried for a player
- **THEN** no other participant's fills are included

### Requirement: Reconnect reconciliation

The system SHALL rebuild the tab from resync frames plus the history query on
reconnect, replacing the mirror rather than merging into it.

#### Scenario: Return shows the full picture

- **WHEN** a player returns after an absence with fills and closes behind them
- **THEN** positions, account, working orders and history all reflect the server state
