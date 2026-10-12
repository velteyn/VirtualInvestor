# futures Specification

## Purpose
Intraday cash-settled futures give players linear, notional-levered exposure to
the existing fictional underlyings. One expiry per round at the closing bell
keeps the feature explainable: there is no overnight carry, no physical
delivery, and every contract settles to the closing auction price.

## Requirements

### Requirement: Futures series listing

The system SHALL list one futures series per tradeable equity underlying (and
optionally the index), each carrying metadata: series symbol, underlying ticker,
contract multiplier, price tick, quantity grid, initial and maintenance margin
per contract, and expiry fixed at the round's closing bell.

#### Scenario: Futures book searchable

- **WHEN** a client searches for a futures series by symbol or underlying
- **THEN** the system returns the series with its metadata and asset class FUT

#### Scenario: Unknown series rejected

- **WHEN** an order names a futures series that is not listed
- **THEN** the system rejects the order and reports the unknown series

### Requirement: Margin held per contract

The system SHALL lock the initial margin in cash when a futures position is
opened, SHALL release it as the position closes, and SHALL treat equity net of
locked margin against the maintenance requirement on realism presets that
enable margin. The default initial margin SHALL be 20% of notional value
(5x leverage, per the WST 2001 tutorial canon) and SHALL be configurable per
series in launch config.

#### Scenario: Opening locks margin

- **WHEN** a participant opens 2 contracts with an initial margin of 500 each
- **THEN** 1000 of cash is locked and buying power falls by the same amount

#### Scenario: Default margin is twenty percent

- **WHEN** a series lists no explicit initial margin
- **THEN** the initial margin equals 20% of the contract notional at reference
  price

#### Scenario: Closing releases margin

- **WHEN** the participant closes those contracts
- **THEN** the locked margin is released back to free cash

### Requirement: Mark-to-market

The system SHALL revalue every open futures position to the latest traded price
of its series at each mark cadence, SHALL move the variation in cash between
counterparties' variation balances deterministically, and SHALL produce
identical balances in event-log replays.

#### Scenario: Favorable move credits variation

- **WHEN** the series price rises after a participant buys 1 contract
- **THEN** the participant's position shows the gain and equity reflects it

#### Scenario: Replay matches live MTM

- **WHEN** a round with futures fills is rebuilt from seed and event log
- **THEN** every futures balance matches the original run exactly

### Requirement: Cash settlement at the bell

At the closing auction the system SHALL settle every open futures position in
cash against the underlying's closing price, SHALL close the positions, and
SHALL fold the settlement P&L into realized P&L with no delivery.

#### Scenario: Long settles above entry

- **WHEN** the closing price exceeds a long future's entry price
- **THEN** the holder is credited multiplier times the difference per contract

#### Scenario: Nothing survives the bell

- **WHEN** settlement completes
- **THEN** no participant holds an open futures position

### Requirement: Halt inheritance

A halt of the underlying equity SHALL halt its futures series for the same
period, refusing new orders with a reason naming the halted underlying.

#### Scenario: Underlying halt blocks futures orders

- **WHEN** the underlying is halted and an order for its future arrives
- **THEN** the order is refused until the halt expires
