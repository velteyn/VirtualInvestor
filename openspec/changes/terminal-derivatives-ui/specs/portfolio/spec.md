# Spec Delta

## MODIFIED Requirements

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
