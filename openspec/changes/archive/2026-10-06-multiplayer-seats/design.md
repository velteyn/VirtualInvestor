# Design

## Context

See `proposal.md` for motivation. Current state: `RunningRound` builds
exactly one `HumanParticipant("human")` plus fixed rivals/institutions in its
constructor; everything downstream keys off opaque participant ids.
`GameService` links one player per round for purse reset/write-back keyed on
the `"human"` seat. The socket binds `(roundId, participantId)` with an
approved login token.

## Goals / Non-Goals

**Goals:** N-human rounds with per-seat purses, locked at start, solo games
byte-identical.

**Non-Goals:** late joins and bot-seat takeovers (named follow-up, rooms lock);
room master/waiting mechanics (the `game-lobby` change owns them — here rooms
are just rounds whose roster happens to hold several humans); matchmaking UI.

## Decisions

### D1. Roster injection at construction, not mutation later

**Decision:** `RunningRound` takes extra `HumanParticipant`s in its constructor
(default: none) and treats them like the first human everywhere — orders,
margin, leaderboard, fees, journal. No add/remove API; locked rooms never need
mid-round roster changes.

**Rationale:** The lock decision makes mutation unnecessary; construction-time
injection keeps the world immutable after start and the determinism story
untouched (extra humans are just more seed-independent participants).

**Alternatives:** an `addHuman()` pre-start API (same power, wider lifecycle
surface for no gain).

### D2. Seats `human`, `human-2`… in join order, mapped per round

**Decision:** The first human keeps `"human"` (every test, script and e2e
assumption survives); later seats number up. Edge keeps a per-round
player-to-seat map used for purse reset/write-back and join responses.

**Rationale:** Readable journals and displays; the mapping table is the only
new state, and the purse code already wants it.

**Alternatives:** player ids as seats (unique forever, unreadable everywhere).

### D3. Backfill first-N, banks by bot name

**Decision:** Unclaimed seats fill from the roster head (MARK first); bank
assignment keys off the bot's name, not its seat index, so LEDGER always banks
(and behaves) identically regardless of headcount.

**Rationale:** Solo games stay exactly today's game; bot behavior never shifts
under joining humans, protecting calibration.

### D4. Join gate checks the seal

**Decision:** Join resolves the round, refuses with `started` when the clock
runs, otherwise claims the next free seat for the approved identity. The
existing `not-approved` path is untouched.

**Rationale:** One gate, two rejections (`not-approved` for who, `started` for
when); spectators keep watching either way.

## Risks / Trade-offs

- **Roster-shape tests move** → fixtures asserting 9 participants or the lone
  `"human"` need the injected model; mechanical, concentrated in runtime tests.
- **Six humans, zero bots** → valid but untested territory for market texture
  (no market-maker quotes from rivals; institutions still quote) — acceptable,
  flag for the first all-human calibration.
- **Purse fan-out** → write-back loops seats instead of one account; each seat
  is independent, so no cross-talk by construction.

## Migration Plan

Additive: new ctor overload (old signature delegates with no extra humans),
new map in edge, new join branch. Rollback is reverting; solo behavior is
covered by the existing suite unchanged.

## Open Questions

None that change the approach.
