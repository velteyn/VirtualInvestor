# options Specification

## Purpose
Intraday European calls and puts give players convex payoffs — hedging, defined
risk longs, and premium harvesting — over the existing fictional underlyings.
One expiry per round at the closing bell with cash settlement keeps assignment
and delivery out of scope: an option is either worth its intrinsic value at the
bell or it expires worthless.

## Requirements

### Requirement: Option series listing

The system SHALL list, per tradeable underlying, a small fixed set of strikes
(at-the-money plus fixed steps above and below at listing time) in both call
and put flavors, each carrying metadata: series symbol, underlying ticker,
call/put flag, strike, premium tick, quantity grid, and expiry fixed at the
round's closing bell.

#### Scenario: Chain lists around the reference price

- **WHEN** a client requests the option chain for an underlying
- **THEN** the system returns call and put series at each listed strike with
  their metadata and asset class OPT

#### Scenario: Unknown series rejected

- **WHEN** an order names an option series that is not listed
- **THEN** the system rejects the order and reports the unknown series

### Requirement: Deterministic premia

Every option premium SHALL be a pure function of the round seed, the ordered
event log, and the underlying's observable state (price, time to bell,
configured volatility), so the same inputs always yield the same premium and
replays stay exact.

#### Scenario: Same state, same premium

- **WHEN** the same underlying state recurs in a replay
- **THEN** the quoted premium for a given series is identical to the original run

### Requirement: Premium settlement at fill

On each option fill the system SHALL move cash premium (price times quantity)
from buyer to seller immediately, SHALL open the buyer's long (or close the
seller's short) position, and SHALL apply the deterministic fee schedule to
both sides before emitting events.

#### Scenario: Buyer pays, seller receives

- **WHEN** a 10-contract call fill executes at a premium of 1.50
- **THEN** 15.00 moves from buyer cash to seller cash and both fee deductions
  are recorded

### Requirement: Cash auto-exercise at the bell

At the closing auction the system SHALL cash-settle every open option series:
in-the-money contracts pay intrinsic value (call: close minus strike; put:
strike minus close, floored at zero) times quantity, out-of-the-money contracts
expire worthless, and all option positions close with settlement P&L folded
into realized P&L.

#### Scenario: ITM call pays intrinsic

- **WHEN** the closing price exceeds a call's strike by 2.00 and 5 contracts
  are open
- **THEN** each long holder is credited 10.00 and the position closes

#### Scenario: OTM option expires worthless

- **WHEN** the closing price leaves an option out of the money
- **THEN** the position closes with no settlement payment

### Requirement: Halt inheritance

A halt of the underlying equity SHALL halt its option series for the same
period, refusing new orders with a reason naming the halted underlying.

#### Scenario: Underlying halt blocks option orders

- **WHEN** the underlying is halted and an order for its option arrives
- **THEN** the order is refused until the halt expires
