# Delta — terminal-trading-ui (change: join-price-seed)

## ADDED Requirements

### Requirement: Instant prices on join

The terminal SHALL seed watchlist prices and ticket marks from the symbol's
last history close at mount (and on ticket symbol change) whenever no live
print exists yet, so prices, previews and touch-prefill work from the first
second. The first live print or depth frame overwrites the seed.

#### Scenario: Fresh join shows prices

- **WHEN** the player joins a running round with no prints received yet
- **THEN** the watchlist shows last closes and the ticket LIMIT prefills the
  underlying's close instead of zero
