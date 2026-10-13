# Spec Delta

## MODIFIED Requirements

### Requirement: Ticket entry

The system SHALL provide a ticket with side toggle, order type selector,
quantity input, limit price (LIMIT/STOP_LIMIT only) and stop price
(STOP/STOP_LIMIT only), and SHALL transmit exactly the shown values on confirm.
For derivative series the ticket SHALL offer a series picker (underlying, then
series) and SHALL transmit the chosen series symbol with exactly the shown
values on confirm. The ticket SHALL open in a product mode — Shares, Futures,
or Options — that reshapes the form: Futures shows a leverage readout and an
expiry badge; Options shows a call/put toggle and a strike ladder with live
premia per strike; Shares shows today's form unchanged.

#### Scenario: Limit buy with size

- **WHEN** the player sets BUY, LIMIT, 50 shares at 640.20 and transmits
- **THEN** a limit buy for 50 at 640.20 reaches the round

#### Scenario: Type-gated fields

- **WHEN** the player selects MARKET
- **THEN** price fields are hidden and no price is sent

#### Scenario: Futures ticket transmits series

- **WHEN** the player picks an underlying's future, sets side and quantity,
  and confirms
- **THEN** the transmitted order names that series symbol with the shown values

#### Scenario: Option ticket transmits series

- **WHEN** the player picks a call or put series and confirms
- **THEN** the transmitted order names that series symbol with the shown values

#### Scenario: Mode switch reshapes the form

- **WHEN** the player switches the ticket to Options mode
- **THEN** a call/put toggle and strike ladder appear, and the transmitted
  order names the chosen series
