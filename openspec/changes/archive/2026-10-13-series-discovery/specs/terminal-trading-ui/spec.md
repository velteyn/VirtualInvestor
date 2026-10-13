# Delta — terminal-trading-ui (change: series-discovery)

## ADDED Requirements

### Requirement: Searchable watchlist

The watchlist SHALL offer a symbol search box backed by the server search and
SHALL let the player add and remove rows. The list persists per browser in
`localStorage` and defaults to the current five equities. Added series rows
chart like equities when selected.

#### Scenario: Add a series to the watchlist

- **WHEN** the player searches `NVDO-C` and adds a call row
- **THEN** the row persists across reloads and clicking it switches the
  chart to that series
