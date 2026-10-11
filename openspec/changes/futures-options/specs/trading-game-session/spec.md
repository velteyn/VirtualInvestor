# Spec Delta

## MODIFIED Requirements

### Requirement: Margin calls and liquidation

On realism presets that enable margin, the system SHALL issue a margin call and
liquidate positions when a participant's equity falls below the maintenance
requirement. Equity for the call SHALL include futures variation and option
market value; liquidation SHALL close derivative positions (futures at current
series price, options at current premium or intrinsic) alongside equity
positions until the requirement is restored.

#### Scenario: Position liquidated on margin call

- **WHEN** a participant's equity falls below the maintenance margin
- **THEN** the system liquidates enough of the participant's positions to restore the requirement and notifies the participant

#### Scenario: Derivatives liquidated first when most levered

- **WHEN** a margin call fires on an account whose largest exposure is futures
- **THEN** liquidation reduces the futures exposure before touching
  unencumbered equity positions

### Requirement: Session settlement

At the closing bell the system SHALL expire all futures and option series in
cash against their underlyings' closing prices, then mark all remaining equity
positions to the closing price, force-close positions held on intraday margin,
compute final equity, and produce the round's ranking. Settlement order SHALL
be deterministic: closing auction prices first, then derivatives expiry and
exercise, then equity marking and forced closes.

#### Scenario: Ranking after close

- **WHEN** the closing auction concludes
- **THEN** final equity is computed for every participant and the leaderboard is finalized

#### Scenario: Derivatives settle before ranking

- **WHEN** the closing auction concludes with open futures and option positions
- **THEN** expiry and exercise payments are applied before final equity and
  ranking are computed
