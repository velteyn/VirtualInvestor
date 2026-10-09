# Spec Delta

## ADDED Requirements

### Requirement: Signal validity windows

Every headline signal SHALL carry a validity window after which headline
reactions stop, while the absorbed fundamental move persists — so fast news
burns out for bots and slow-burn shocks stay tradeable for their duration.

#### Scenario: Expired headlines are never read

- **WHEN** a bot's perception delay hasn't elapsed when a signal's window closes
- **THEN** it never reacts to that headline, as if never read

#### Scenario: Absorbed value survives expiry

- **WHEN** a signal expires
- **THEN** the fundamental repricing it caused remains, and price-based
  strategies continue trading the gap

#### Scenario: Slow shocks outlive fast money

- **WHEN** a long-validity shock (sector supply disruption) is released
- **THEN** delayed institutional takes still fire within the window while
  short-validity earnings stop moving bots after days
