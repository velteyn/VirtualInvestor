# market-simulation Specification

## Purpose
Defines the deterministic market core: the instrument universe, the hybrid price-formation model, order book and matching, real-time market data, intraday sessions with auctions, and the market rules that make trading behave like a real venue.

## Requirements

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

### Requirement: Deterministic world

The market state SHALL be a pure function of the round seed and the ordered event log; the same seed and inputs SHALL produce identical state transitions, so any round can be reconstructed and replayed exactly.

#### Scenario: Replay reproduces state

- **WHEN** a stored round is rebuilt from its seed and event log
- **THEN** every price, fill, and account balance matches the original run

### Requirement: Hybrid fundamental value

Each instrument SHALL have a latent fundamental value driven by a market factor, a sector factor, idiosyncratic diffusion, discrete jumps, and order-flow impact. The fundamental SHALL persist for the round and SHALL be the anchor that market making quotes around, and SHALL move in response to net aggressive flow so that trading influences the valuation.

#### Scenario: Market factor moves many names

- **WHEN** the market factor changes
- **THEN** instruments with exposure to that factor adjust their fundamental value in the same direction

#### Scenario: Idiosyncratic move stays local

- **WHEN** an idiosyncratic shock affects one instrument
- **THEN** other instruments' fundamentals remain largely unchanged

#### Scenario: Flow moves the fundamental

- **WHEN** net aggressive flow occurs in an instrument
- **THEN** that instrument's fundamental value adjusts in the direction of the flow, in addition to the factor, diffusion and jump terms

### Requirement: Order-driven price discovery

The traded price SHALL be determined by order book matching rather than set directly; resting orders, market making, and incoming flow SHALL determine the last traded price, and a persistent deviation of price from fundamental value SHALL feed back into the fundamental rather than sitting only beside it.

#### Scenario: Player order moves the price

- **WHEN** a large aggressive order consumes resting depth
- **THEN** the last traded price moves away from the prior price in the order's direction

#### Scenario: Price reverts as flow fades

- **WHEN** aggressive flow stops and value-oriented participants trade toward the fundamental
- **THEN** the traded price tends back toward the fundamental value

#### Scenario: Deviation feeds back into the fundamental

- **WHEN** the traded price stays above the fundamental while buying persists
- **THEN** the fundamental is pulled upward, so the two converge rather than the gap persisting indefinitely

### Requirement: Order book and matching engine

The system SHALL maintain a price-time-priority order book per instrument and SHALL match orders supporting at least MARKET, LIMIT, STOP, and STOP-LIMIT types, with partial fills and multi-level depth.

#### Scenario: Limit orders rest

- **WHEN** a limit order does not cross the book
- **THEN** it rests at its price with time priority and appears in market depth

#### Scenario: Crossing order fills partially

- **WHEN** an aggressive order exceeds the quantity available at better prices
- **THEN** it fills the available quantity and either rests the remainder or is rejected according to its type

#### Scenario: Stop triggers

- **WHEN** the last traded price crosses a resting stop order's trigger
- **THEN** the stop converts to a market or limit order per its type

### Requirement: Market data publication

The system SHALL publish market data in real time: best bid and offer with sizes, configurable depth levels, trade prints, and OHLCV bars at supported intervals, each carrying a timestamp aligned to the world clock.

#### Scenario: Depth levels stream

- **WHEN** the book for an instrument changes
- **THEN** subscribed clients receive updated bid and ask levels and sizes

#### Scenario: Trade prints publish

- **WHEN** a match occurs
- **THEN** a trade print with price, quantity, and timestamp is published

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
