# order-ticket Specification

## Purpose
Sized, previewed order entry: the ticket is the terminal's single transmit
path, teaching costs before anything is sent.

## Requirements

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

### Requirement: Sizing memory and lots

The system SHALL remember the last used quantity, offer lot buttons including
Max (buying power over price, floored), and SHALL default new tickets to the
remembered size.

#### Scenario: Remembered size

- **WHEN** the player last traded 50 shares and opens the ticket
- **THEN** quantity starts at 50

### Requirement: Touch-prefill pricing

The system SHALL prefill the limit/stop price from the current touch (ask for
buys, bid for sells), following it live until the player edits the field.

#### Scenario: Prefill tracks the market

- **WHEN** the ticket opens for a buy while the ask moves
- **THEN** the price field tracks the ask until typed in

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

### Requirement: Inline rejection reasons

The system SHALL keep typed values on a rejected transmit and SHALL display
the server's reason next to the offending field.

#### Scenario: Rejection teaches

- **WHEN** the server rejects for quantity grid
- **THEN** the ticket shows the reason and preserves every field for one-edit fixing

### Requirement: Prefill instead of transmit

The system SHALL NOT transmit from chart menus or Buy/Sell buttons; they
prefill the ticket (side, type, chart price) for review, and drag-to-modify of
working lines SHALL keep working unchanged.

#### Scenario: Right-click prefills

- **WHEN** the player right-clicks a chart price for a limit buy
- **THEN** the ticket opens with BUY, LIMIT and that price, quantity from memory, nothing sent
