# Spec Delta

## Purpose

Defines how trading moves the market: the transient and permanent impact of net aggressive flow on an instrument's latent fundamental value, scaled by liquidity, and the reversion that tethers price to a reference.

## ADDED Requirements

### Requirement: Order flow moves the fundamental

Net aggressive flow in an instrument SHALL continuously adjust that instrument's latent fundamental value: net buy flow SHALL raise it and net sell flow SHALL lower it, so trading changes the world's valuation rather than only the traded price.

#### Scenario: Cumulative buying raises the fundamental

- **WHEN** sustained aggressive buying occurs in an instrument
- **THEN** that instrument's fundamental value rises above where it would have been

#### Scenario: Selling lowers it

- **WHEN** sustained aggressive selling occurs in an instrument
- **THEN** that instrument's fundamental value falls

### Requirement: Transient and permanent impact

Flow impact SHALL have two components: a transient part that reverts over time and a permanent part that persists, so a burst moves price temporarily while sustained flow durably re-values the instrument.

#### Scenario: A burst reverts

- **WHEN** a single large order trades and no further flow follows
- **THEN** most of its impact reverts and the fundamental settles near its prior path

#### Scenario: Sustained flow persists

- **WHEN** flow continues in the same direction over many ticks
- **THEN** a permanent component accumulates and the fundamental does not fully revert

### Requirement: Impact scales with illiquidity

The size of the impact per unit of flow SHALL depend on the instrument's liquidity (free float and available depth), so an order moves an illiquid instrument more than a liquid one.

#### Scenario: Illiquid name moves more

- **WHEN** the same order quantity trades in an illiquid instrument and a liquid one
- **THEN** the illiquid instrument's fundamental moves more

### Requirement: Reversion tethers price to a reference

A reversion term SHALL pull the traded price and the fundamental back toward a reference, so the market does not drift without bound and mean reversion is observable.

#### Scenario: Price reverts after flow fades

- **WHEN** aggressive flow stops and value-oriented participants trade toward the fundamental
- **THEN** the traded price tends back toward the fundamental

#### Scenario: Unbounded drift prevented

- **WHEN** no flow occurs for a sustained period
- **THEN** the fundamental remains tethered to its reference rather than diverging freely

### Requirement: Impact is deterministic and replayable

The flow impact SHALL be a deterministic function of the ordered event stream, so a round replayed from its seed and log reproduces every fundamental value including flow-induced moves.

#### Scenario: Replay reproduces flow impact

- **WHEN** a round with heavy flow is rebuilt from its seed and event log
- **THEN** every fundamental value matches the original run

### Requirement: Impact is observable and tunable

The transient and permanent impact coefficients, the reversion strength, and the current
fundamental-versus-reference gap SHALL be exposed so they can be calibrated and inspected.

#### Scenario: Calibration reports impact

- **WHEN** the calibration harness runs a round with flow
- **THEN** it reports the impact and reversion behavior for tuning
