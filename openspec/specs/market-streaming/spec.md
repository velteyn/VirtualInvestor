# market-streaming Specification

## Purpose
Defines how the running world pushes its output to connected clients in real time, and how a client resynchronizes after a dropped connection.

## Requirements

### Requirement: Stream all world output

The server SHALL broadcast, as the world produces them, bars, trade prints, market depth, order and position updates, account readings and news to every WebSocket subscriber for the round.

#### Scenario: Trade reaches subscribers

- **WHEN** the world prints a trade
- **THEN** every connected subscriber receives a trade frame

#### Scenario: News reaches subscribers

- **WHEN** the news engine releases an item
- **THEN** subscribers receive a news frame with its source, scope and text

### Requirement: Coalesced depth

Depth updates for one instrument SHALL be coalesced within a short window so a fast book does not flood subscribers; the latest winning update SHALL be the one delivered.

#### Scenario: Rapid depth changes collapse

- **WHEN** an instrument's book changes several times within the window
- **THEN** subscribers receive the latest depth rather than every intermediate update

### Requirement: Ordered, resynchronizable stream

Streamed events SHALL carry their sequence number and tick, so a subscriber that reconnects can request the events it missed since the last sequence it saw and rebuild its view.

#### Scenario: Reconnect catches up

- **WHEN** a subscriber reconnects and reports its last seen sequence
- **THEN** the server sends the events after that sequence

### Requirement: Slow or dead subscribers do not stall the world

The loop SHALL NOT block on a slow or disconnected subscriber; a failing send SHALL drop or skip that subscriber without affecting the tick or other subscribers.

#### Scenario: Slow client does not slow the loop

- **WHEN** one subscriber's socket backs up
- **THEN** the world keeps advancing and other subscribers keep receiving

### Requirement: Stream respects the session and the round

The server SHALL stream only for a round that is running or settling, SHALL stop streaming a settled round's market data, and SHALL deliver the round's settled standings.

#### Scenario: No live bars after settlement

- **WHEN** the round has settled
- **THEN** market-data streaming stops and the final leaderboard is delivered
