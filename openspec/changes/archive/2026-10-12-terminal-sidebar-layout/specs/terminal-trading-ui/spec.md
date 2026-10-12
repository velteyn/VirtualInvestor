# Spec Delta

## MODIFIED Requirements

### Requirement: Live terminal layout

The terminal SHALL present a single screen: on the left, a candlestick chart
(carrying the market overlays) above a tabbed dock (Positions, Portfolio,
company, newsroom, News wire); on the right, a full-height sidebar holding
the rivals leaderboard, the watchlist, and the order ticket down to the
footer. The page SHALL NOT scroll: the sidebar and the dock scroll internally
within the viewport height.

#### Scenario: Terminal opens on a running round

- **WHEN** the player starts a round from the launch pad
- **THEN** the terminal opens with the chart, the rivals, watchlist and news panels, the order ticket and the positions strip visible

#### Scenario: Panels are populated

- **WHEN** the round is running
- **THEN** the chart shows live bars, the rivals show live standings, the watchlist shows live prices, and the news panel shows wire headlines

#### Scenario: Sidebar spans full height

- **WHEN** the terminal is open on a wide screen
- **THEN** rivals, watchlist and ticket stack in a right sidebar reaching the
  footer, and no page-level scrollbar appears

#### Scenario: News wire lives in the dock

- **WHEN** the player opens the dock tabs
- **THEN** a News tab shows the same wire headlines previously stacked in the
  sidebar
