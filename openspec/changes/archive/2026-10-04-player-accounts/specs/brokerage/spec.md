# brokerage Specification

## Purpose
Fictional banks as brokerages: each bank publishes a fee schedule, every
player (human or bot) trades through one chosen bank, and every execution pays
the schedule deterministically so replays stay exact and costs are visible.

## ADDED Requirements

### Requirement: Bank choice at registration

The system SHALL offer the list of banks with their fee terms at registration
and SHALL require the player to choose one before the account is created.

#### Scenario: Registration without a bank refused

- **WHEN** a registration omits the bank choice
- **THEN** registration is refused until a listed bank is chosen

#### Scenario: Unknown bank refused

- **WHEN** a registration names a bank that does not exist
- **THEN** registration is refused with an unknown-bank error

### Requirement: Published fee schedules

Each bank SHALL publish a per-share commission, a minimum fee per ticket, and
an optional custody rate, all visible to the player before choosing.

#### Scenario: Terms visible before choice

- **WHEN** a player views the bank list
- **THEN** each bank shows its per-share commission, ticket minimum and custody terms

### Requirement: Deterministic per-execution deduction

Every fill SHALL deduct `max(ticket minimum, per-share commission x filled
quantity)` in cash from the executing participant, as a pure function of the
fill, identically for humans and bots.

#### Scenario: Commission on a fill

- **WHEN** a 100-share fill executes for a bank charging 0.01 per share with a 1.00 minimum
- **THEN** exactly 1.00 is deducted from that participant's cash

#### Scenario: Same fill, same fee

- **WHEN** the same fill is replayed from the event log
- **THEN** the deducted fee is identical to the original run

### Requirement: Visible fee totals

The system SHALL report each player's total fees paid for the round alongside
the account, so costs are observable and comparable.

#### Scenario: Fees visible after trading

- **WHEN** a player with executions views the account
- **THEN** the total fees paid this round are shown
