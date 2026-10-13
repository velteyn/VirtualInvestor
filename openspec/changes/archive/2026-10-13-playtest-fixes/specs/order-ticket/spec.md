# Delta — order-ticket (change: playtest-fixes)

## ADDED Requirements

### Requirement: Chain fallback

The ticket SHALL load the derivative chain from the dedicated chain endpoint
and, when that endpoint is unavailable, SHALL fall back to substring search
for the underlying's chain instead of leaving the ladder empty.

#### Scenario: Old server still lists strikes

- **WHEN** the chain endpoint answers 404 and the player selects underlying
  `NVDO` in Options mode
- **THEN** the ladder still fills from search and strike selection transmits
