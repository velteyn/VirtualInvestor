# Spec Delta

## MODIFIED Requirements

### Requirement: Chart is fed by the live stream

The chart SHALL load history from the server and update from the live stream — bars, trades and depth — rather than a seeded placeholder, and SHALL draw the working-order and position overlays. History loads SHALL work for
derivative series symbols exactly as for spot.

#### Scenario: Chart ticks

- **WHEN** the server publishes a new bar or trade
- **THEN** the chart updates without a reload

#### Scenario: Depth drives the ladder

- **WHEN** the server publishes depth for the chart's instrument
- **THEN** the DOM ladder shows the current bid and ask levels and sizes

#### Scenario: Series chart opens with history

- **WHEN** the player charts a listed future or option before it trades
- **THEN** warm-up bars render instead of an empty chart
