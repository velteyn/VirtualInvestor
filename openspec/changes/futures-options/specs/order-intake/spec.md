# Spec Delta

## MODIFIED Requirements

### Requirement: Pre-trade validation with explicit reasons

Every order SHALL be validated before it reaches the book: tick grid, quantity
grid and minimum, short-sale availability, halted instruments and price bands,
each producing a specific rejection reason. Orders naming a derivative series
SHALL additionally be validated for series existence, expiry (no orders after
the bell), series tick and quantity grids, and halts of the underlying
instrument, each producing a specific rejection reason.

#### Scenario: Off-tick price rejected

- **WHEN** a limit price is not on the instrument's tick grid
- **THEN** the order is rejected with a reason naming the tick

#### Scenario: Halted instrument refuses orders

- **WHEN** an instrument is halted by its circuit breaker
- **THEN** orders for it are refused until the halt expires

#### Scenario: Unknown derivative series rejected

- **WHEN** an order names a futures or option series that is not listed
- **THEN** the order is rejected with a reason naming the unknown series

#### Scenario: Order after expiry refused

- **WHEN** an order names a series whose expiry has passed
- **THEN** the order is rejected with a reason naming the expiry

### Requirement: Matching and account application

Accepted orders SHALL be matched against the book, and every fill SHALL be
applied to both counterparties' accounts (cash, position, realized P&L) before
the resulting events are emitted. Futures fills SHALL lock or release margin
and move variation on mark-to-market; option fills SHALL move premium from
buyer to seller; all SHALL be deterministic functions of the fill.

#### Scenario: A fill updates both accounts

- **WHEN** an order matches a resting order
- **THEN** both participants' positions and cash reflect the fill

#### Scenario: Option fill moves premium

- **WHEN** an option order matches
- **THEN** buyer cash falls and seller cash rises by premium times quantity
  before events are emitted

### Requirement: Order lifecycle events

The server SHALL emit the order lifecycle — accepted, rested, triggered,
partially filled, filled, cancelled, rejected — plus a trade print per
execution, so subscribers can reconstruct the book and P&L. Expiry and
exercise-driven closes SHALL emit close events carrying series, quantity, and
settlement price so subscribers can reconstruct derivatives P&L.

#### Scenario: Fill emits print and status

- **WHEN** an order fills
- **THEN** a trade print and the order's filled status are emitted in order

#### Scenario: Expiry emits settlement close

- **WHEN** a derivative position closes at expiry or exercise
- **THEN** a close event with series, quantity, and settlement price is emitted
