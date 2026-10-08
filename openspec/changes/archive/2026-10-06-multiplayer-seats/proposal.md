# Proposal

## Why

Rounds construct exactly one human slot, so a second player has nowhere to
sit: joining, rooms and every multiplayer flow stop at roster construction.
The simulation itself is already multiplayer-shaped (everything keys off
opaque participant ids) — only the roster is single-player.

## What Changes

- Rounds accept extra humans at construction alongside the fixed bot roster:
  1 creator human + 5 rivals + 3 institutions stays the default; additional
  approved players arrive as further human seats.
- Fixed 6 ranked seats per room: humans claim in join order, unclaimed seats
  backfill first-N by roster order, banks assign by bot name, institutions
  stay unranked and untouched.
- Seat ids `human`, `human-2`… in join order, mapped per round to player ids
  for purse write-back; display names carry the username, so logs and journals
  stay readable.
- Rooms lock at master start: no late joins and no bot-seat takeovers in this
  change (takeovers are a named follow-up).
- Existing roster-shape tests move to the injected model; solo games
  (1 human + 5 rivals) behave byte-for-byte as today.

## Capabilities

### New Capabilities

- `multiplayer-seats`: roster injection, join-order seat allocation with bot
  backfill, per-seat purse mapping, locked-at-start rooms.

### Modified Capabilities

- `trading-game-session`: the participant model gains multi-human seats; the
  single-human round becomes the 1-human case of the general model.

## Impact

- Runtime roster construction is the structural cut; matching, settlement,
  fees, journal and leaderboard are untouched (all participant-keyed).
- Unblocks `game-lobby` (listing, rooms, master start), which assumes this.
- Calibration: solo games unchanged; multi-human economics get their own
  baseline on first measurement.
