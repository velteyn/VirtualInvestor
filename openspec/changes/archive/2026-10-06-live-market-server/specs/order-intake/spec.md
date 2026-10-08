# Spec Delta

## Purpose

Defines how the running server accepts, validates and matches orders from any participant, applies the fills to accounts, and reports the resulting order lifecycle.

## ADDED Requirements

### Requirement: Commands from any participant

The server SHALL accept `place`, `modify` and `cancel` commands naming a participant, from the human player or a bot, through a single intake path, and SHALL refuse a command whose sender does not match the named participant.

#### Scenario: Bot and human use the same path

- **WHEN** a bot and the human player submit equivalent orders
- **THEN** they are validated and matched identically

#### Scenario: Spoofed participant refused

- **WHEN** a command names a participant other than the sender
- **THEN** the server refuses it and reports the mismatch

### Requirement: Pre-trade validation with explicit reasons

Every order SHALL be validated before it reaches the book: tick grid, quantity grid and minimum, short-sale availability, halted instruments and price bands, each producing a specific rejection reason.

#### Scenario: Off-tick price rejected

- **WHEN** a limit price is not on the instrument's tick grid
- **THEN** the order is rejected with a reason naming the tick

#### Scenario: Halted instrument refuses orders

- **WHEN** an instrument is halted by its circuit breaker
- **THEN** orders for it are refused until the halt expires

### Requirement: Matching and account application

Accepted orders SHALL be matched against the book, and every fill SHALL be applied to both counterparties' accounts (cash, position, realized P&L) before the resulting events are emitted.

#### Scenario: A fill updates both accounts

- **WHEN** an order matches a resting order
- **THEN** both participants' positions and cash reflect the fill

### Requirement: Idempotent commands

Every command SHALL carry a client token, and a repeated command with the same token SHALL NOT be placed twice, so a retried request after a timeout cannot double-fill.

#### Scenario: Retry does not double-fill

- **WHEN** the same place command with the same client token is received twice
- **THEN** only one order is created

### Requirement: Order lifecycle events

The server SHALL emit the order lifecycle — accepted, rested, triggered, partially filled, filled, cancelled, rejected — plus a trade print per execution, so subscribers can reconstruct the book and P&L.

#### Scenario: Fill emits print and status

- **WHEN** an order fills
- **THEN** a trade print and the order's filled status are emitted in order
