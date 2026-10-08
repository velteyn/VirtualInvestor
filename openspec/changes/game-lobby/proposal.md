# Proposal

## Why

Login today drops every player into an isolated world: there is no meeting
place, no shared games, and no way to join a running round. Multiplayer needs
a lobby — one screen after login where games are created, found and joined.

## What Changes

- A lobby screen right after login listing games: single-player sessions and
  rooms, each with state (waiting/live), preset, time left, seats taken and
  the leader.
- Create single-player game exactly as today (private world, starts at once).
- Fixed 6 ranked seats per room: humans claim seats in join order, unclaimed
  seats are filled by bots first-N by roster order (1 human + 5 rivals plays
  exactly like today); banks assign by bot name so behavior never shifts with
  headcount; the 3 unranked institutions are untouched.
- Create room: configuration, world built but clock held in a waiting state;
  the creator is room master.
- Join room: any approved player takes a free seat while waiting. Rooms lock
  at master start: no late joins, no bot-seat takeovers — latecomers wait in
  the lobby or start their own room. (Takeovers are a named follow-up, not
  this change.)
- Master start: the master starts the held world when ready; a room with all
  seats filled may start itself.

## Capabilities

### New Capabilities

- `game-lobby`: lobby listing, room creation with waiting state, joining,
  master start.

### Modified Capabilities

- `trading-game-session`: rounds gain waiting/room states and multi-human
  seats alongside the single-human model.

## Impact

- Requires multi-human seats first (roster injection — separate change, this
  proposal assumes it): lobby joining has nowhere to sit without it.
- Edge: public live-games listing, room holding (clock gate), master-start
  authorization (master token or creator check).
- Web: lobby screen replaces the direct-to-config launchpad flow; session
  config moves into create dialogs.
- Round lifecycle gains WAITING; settlement, purse and fees are untouched.
