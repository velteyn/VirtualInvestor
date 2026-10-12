# newsroom-narrator Specification

## Purpose

Give the market a voice: short articles for news releases, persisted for late
joiners and readable in a newsroom tab, generated out-of-band so prose never
touches the simulation.

## Requirements

### Requirement: Event-driven articles

The system SHALL generate one article per selected news release, plus a round
preview at start and a recap at end, without delaying or altering the market.

#### Scenario: Earnings become stories

- **WHEN** a company earnings release fires during a round
- **THEN** an article appears on the wire carrying the headline, the company
  profile's context, and the release's magnitude — while the tick engine and
  bot signals proceed exactly as without it

#### Scenario: The market never waits for prose

- **WHEN** the sidecar is slow or unreachable
- **THEN** the headline still publishes immediately, and the article follows
  when ready — or a template article stands in, or nothing does

### Requirement: Articles persist for late joiners

The system SHALL store every article with its round, tick, and subjects, and
serve the round's archive to any participant on request.

#### Scenario: Joining mid-session

- **WHEN** a player joins a round with ten articles already written
- **THEN** the newsroom tab shows all ten, oldest first, with no replays or gaps

### Requirement: Fictional universe in generated text

The system SHALL constrain generated articles to the fictional universe: no
real companies, products, or outlets, and no advice to players.

#### Scenario: Sidecar invents a real name

- **WHEN** a generated body names a real company or tells the reader to buy
- **THEN** the article is dropped (or repaired) before broadcast, and the
  headline stands alone

### Requirement: Newsroom tab

The terminal SHALL offer the round's articles inside the News tab: every wire
row with a story body offers a full-story affordance that opens the complete
article text in place, and the tab's initial load serves the archived
articles so late joiners read the session's story so far. No separate
newsroom tab remains.

#### Scenario: Reading the session's story

- **WHEN** a player opens the News tab mid-round
- **THEN** every article so far is readable without leaving the terminal

#### Scenario: Full story expands in place

- **WHEN** a wire row with a story body is opened
- **THEN** the complete article text shows inline without navigating away,
  and closing it returns to the wire
