# Spec Delta

## MODIFIED Requirements

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
