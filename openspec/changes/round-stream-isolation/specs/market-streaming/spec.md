# Spec Delta

## MODIFIED Requirements

### Requirement: Stream all world output

The server SHALL broadcast, as the world produces them, bars, trade prints, market depth, order and position updates, account readings and news to every WebSocket subscriber for the round.

#### Scenario: Trade reaches subscribers

- **WHEN** the world prints a trade
- **THEN** every connected subscriber receives a trade frame

#### Scenario: News reaches subscribers

- **WHEN** the news engine releases an item
- **THEN** subscribers receive a news frame with its source, scope and text

#### Scenario: Frames stay inside their round

- **WHEN** two rounds run and a socket joined only round A
- **THEN** it receives no bars, prints, depth, orders, positions, account
  readings or news from round B

#### Scenario: Re-join rebinds the socket

- **WHEN** a socket joins round B after round A
- **THEN** round A frames stop and round B frames start, with no cross-talk
