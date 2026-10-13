# portfolio Specification

## Purpose
The resume-grade portfolio: holdings with gains, working orders, account
totals, and the player's fill history — all backed by server state so closing
the browser loses nothing.

## Requirements

### Requirement: Holdings table

The system SHALL show one row per open position with symbol, signed quantity,
average price, last price, market value, invested amount, unrealized gain in
value and percent, and a close action. Rows for futures SHALL show locked
margin and mark-to-market value; rows for options SHALL show premium paid or
received and intrinsic-time breakdown where available; expiring rows SHALL be
marked with their expiry. The table SHALL be sectioned by product — Shares,
Futures, Calls, Puts — under one tab: each section uses its product's columns
(Shares as today; Futures with side, entry, mark, MTM variation, locked
margin, expiry; Calls/Puts with strike, premium, mark, intrinsic vs time
value, ITM/OTM badge, payoff-if-held at current price, expiry), and a section
SHALL render only while it holds rows.

#### Scenario: Holdings derive from streamed state

- **WHEN** the player holds 50 NVDO bought at 640 with last price 645
- **THEN** the row shows qty +50, avg 640, value 32250, invested 32000, unrealized +250 (+0.8%)

#### Scenario: Close flattens from the row

- **WHEN** the player closes a row
- **THEN** an offsetting market order is sent and the row disappears once flat

#### Scenario: Futures row shows margin and MTM

- **WHEN** a participant holds an open future
- **THEN** its row shows locked margin alongside mark-to-market gain or loss

#### Scenario: Expiring row is marked

- **WHEN** a participant holds a position in a series expiring at the bell
- **THEN** its row marks the expiry

#### Scenario: Shares section is unchanged

- **WHEN** the player holds only shares
- **THEN** the Shares section shows today's columns and no other section renders

#### Scenario: Options row splits intrinsic and time value

- **WHEN** the player holds a call with premium above intrinsic value
- **THEN** its row shows strike, premium, mark, intrinsic and time-value parts,
  an ITM/OTM badge, and the payoff if held to the bell at the current price

### Requirement: Account header

The system SHALL show equity with return on starting capital, cash, buying
power, unrealized P&L, realized P&L and total fees paid. Buying power SHALL
account for locked futures margin and short-option margin requirements, and
equity SHALL include futures variation and option market value.

#### Scenario: Header reflects the account

- **WHEN** the account holds cash plus open positions
- **THEN** equity equals cash plus market value and return equals equity over starting capital

#### Scenario: Locked margin reduces buying power

- **WHEN** a participant opens a margined futures position
- **THEN** buying power falls by the locked margin while equity reflects MTM

### Requirement: Per-symbol realized gains

The system SHALL track realized P&L per symbol as positions reduce or flip,
identically in live runs and event-log replays, and SHALL expose it on
position state. Settlement payments from expiry and exercise SHALL fold into
realized P&L per series symbol under the same replay identity.

#### Scenario: Closing half books half the gain

- **WHEN** half of a winning position is sold
- **THEN** half the unrealized gain moves to that symbol's realized total

#### Scenario: Expiry P&L attributed per series

- **WHEN** a futures or option position settles at the bell
- **THEN** its settlement gain or loss appears in realized P&L for that series
  symbol, identically in replays

### Requirement: Fill history query

The system SHALL provide the player's fills for the round — order id, symbol,
side, quantity, price, fee and tick — folded from the round journal, so the
tab's History section shows what executed while the player was away. Fills in
derivative series SHALL additionally carry premium or margin effects and
settlement closes SHALL appear as journal-derived entries.

#### Scenario: History covers the absence

- **WHEN** a fill completes while the player is disconnected
- **THEN** the History section lists it after reconnect with price, quantity and fee

#### Scenario: History is participant-scoped

- **WHEN** the history is queried for a player
- **THEN** no other participant's fills are included

#### Scenario: Derivative fills appear with economics

- **WHEN** a player with option and futures fills views History
- **THEN** each entry shows its premium or margin movement alongside price and fee

### Requirement: Reconnect reconciliation

The system SHALL rebuild the tab from resync frames plus the history query on
reconnect, replacing the mirror rather than merging into it.

#### Scenario: Return shows the full picture

- **WHEN** a player returns after an absence with fills and closes behind them
- **THEN** positions, account, working orders and history all reflect the server state

### Requirement: History spans checkpoints

Fill history SHALL cover the whole round, not just the tail since the last
checkpoint: checkpoint compaction SHALL delete only clock-scaffolding Tick
rows and keep fills, reports and prints for the history query and post-game
review.

#### Scenario: Early fills survive

- **WHEN** a fill completes and several checkpoints pass
- **THEN** the History section still lists it with price, quantity and fee
