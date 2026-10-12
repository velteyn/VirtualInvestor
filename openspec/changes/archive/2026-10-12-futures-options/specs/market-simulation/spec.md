# Spec Delta

## MODIFIED Requirements

### Requirement: Instrument universe

The system SHALL provide a fixed universe of fictional NASDAQ-like equity
instruments plus at least one index, each carrying metadata: ticker, display
name, sector, shares outstanding, free float, price tick, quantity grid,
reference price, base volatility, and factor exposures. The system SHALL
additionally list derivative series — one futures series per tradeable
underlying and a fixed call/put strike set per tradeable underlying, each
carrying its series symbol, underlying ticker, contract terms, and expiry at
the round's closing bell, as defined by the `futures` and `options`
capabilities. No instrument or series SHALL reference a real company name or
trademark.

#### Scenario: Symbol search returns instruments

- **WHEN** a client searches by ticker or name
- **THEN** the system returns matching instruments with their metadata and asset class

#### Scenario: Metadata drives order validation

- **WHEN** an order names an unknown instrument
- **THEN** the system rejects the order and reports the unknown instrument

#### Scenario: Chain search returns derivative series

- **WHEN** a client searches by underlying ticker for derivatives
- **THEN** the system returns the listed futures series and option chain with
  their metadata and asset classes

### Requirement: Market rules

The system SHALL enforce per-instrument market rules: tick-size price rounding,
minimum quantity and quantity grid, short-sale availability, price bands or
circuit breakers that halt trading on extreme moves, and initial and
maintenance margin requirements. Derivative series SHALL carry their own tick,
quantity grid, and margin requirements; a halt of an underlying equity SHALL
halt its derivative series for the same period.

#### Scenario: Circuit breaker halts trading

- **WHEN** an instrument's price moves beyond its configured band within the window
- **THEN** trading in that instrument halts for the configured period and clients are notified

#### Scenario: Sub-grid quantity rejected

- **WHEN** an order quantity is not a multiple of the instrument's quantity grid
- **THEN** the system rejects the order and reports the reason

#### Scenario: Underlying halt propagates to derivatives

- **WHEN** an equity halts and an order for its future or option arrives
- **THEN** the order is refused with a reason naming the halted underlying

### Requirement: Trading session and auctions

The system SHALL run an intraday session consisting of a pre-open phase, an
opening auction that establishes the opening price, a continuous session, and a
closing auction that establishes the closing price. The closing auction price
of each underlying SHALL additionally serve as the settlement price for its
futures and option series expiring at the bell.

#### Scenario: Opening auction sets the open

- **WHEN** the opening auction concludes
- **THEN** the opening price is the price that maximizes matched volume and the continuous session begins from it

#### Scenario: Closing auction sets the close

- **WHEN** the closing auction concludes
- **THEN** a closing price is established and used for settlement

#### Scenario: Close settles derivatives

- **WHEN** the closing auction concludes
- **THEN** every expiring futures and option series settles in cash against its
  underlying's closing price
