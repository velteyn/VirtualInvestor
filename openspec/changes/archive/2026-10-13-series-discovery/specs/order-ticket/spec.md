# Delta — order-ticket (change: series-discovery)

## ADDED Requirements

### Requirement: Chain source

The ticket SHALL load the derivative chain for its underlying from the
dedicated chain endpoint, returning the full listed chain (future, then calls
and puts by strike), and SHALL NOT depend on substring search matching.

#### Scenario: Chain loads without search

- **WHEN** the player selects underlying `NVDO` in Futures mode
- **THEN** the ticket lists its future plus every listed call and put even
  when a substring search would truncate or miss rows
