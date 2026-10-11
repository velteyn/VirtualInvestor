# Spec Delta

## MODIFIED Requirements

### Requirement: Published fee schedules

Each bank SHALL publish a per-share commission, a minimum fee per ticket, and
an optional custody rate, all visible to the player before choosing. Each bank
SHALL additionally publish a per-contract commission and ticket minimum for
futures and for options, visible alongside the equity terms before choosing.

#### Scenario: Terms visible before choice

- **WHEN** a player views the bank list
- **THEN** each bank shows its per-share commission, ticket minimum and custody terms

#### Scenario: Derivatives terms visible before choice

- **WHEN** a player views the bank list
- **THEN** each bank shows its per-contract futures and options commissions
  and ticket minima alongside equity terms

### Requirement: Deterministic per-execution deduction

Every fill SHALL deduct `max(ticket minimum, per-share commission x filled
quantity)` in cash from the executing participant, as a pure function of the
fill, identically for humans and bots. Fills in derivative series SHALL deduct
`max(series ticket minimum, per-contract commission x filled contracts)` under
the same determinism and replay identity.

#### Scenario: Commission on a fill

- **WHEN** a 100-share fill executes for a bank charging 0.01 per share with a 1.00 minimum
- **THEN** exactly 1.00 is deducted from that participant's cash

#### Scenario: Same fill, same fee

- **WHEN** the same fill is replayed from the event log
- **THEN** the deducted fee is identical to the original run

#### Scenario: Commission on a futures fill

- **WHEN** a 5-contract futures fill executes for a bank charging 0.50 per
  contract with a 1.00 minimum
- **THEN** exactly 2.50 is deducted from that participant's cash
