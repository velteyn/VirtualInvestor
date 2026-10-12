# Spec Delta

## MODIFIED Requirements

### Requirement: Ticket entry

The system SHALL provide a ticket with side toggle, order type selector,
quantity input, limit price (LIMIT/STOP_LIMIT only) and stop price
(STOP/STOP_LIMIT only), and SHALL transmit exactly the shown values on confirm.
For derivative series the ticket SHALL offer a series picker (underlying, then
series) and SHALL transmit the chosen series symbol with exactly the shown
values on confirm.

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

### Requirement: Fee preview with buying-power guard

The system SHALL show estimated cost (quantity x price) plus the bank fee and
remaining cash, and SHALL disable Transmit with the reason when the player
cannot afford the order. For futures the preview SHALL show locked margin plus
fee against free cash; for options the preview SHALL show premium plus fee
(and margin requirement for shorts), disabling Transmit with the reason when
unaffordable.

#### Scenario: Preview before send

- **WHEN** the ticket holds 100 shares at 640.00 on a 0.01/share, 2.00-minimum bank
- **THEN** it shows cost $64,000, fee $2.00 and cash after

#### Scenario: Guard blocks oversize

- **WHEN** the order exceeds buying power
- **THEN** Transmit disables with an insufficient-funds reason and nothing is sent

#### Scenario: Futures preview guards on margin

- **WHEN** a futures order's margin plus fee exceeds free cash
- **THEN** Transmit is disabled with a margin reason

#### Scenario: Short option preview guards on margin

- **WHEN** a short option order's margin requirement exceeds availability
- **THEN** Transmit is disabled with a margin reason
