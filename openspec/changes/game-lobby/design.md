# Design

## Context

See `proposal.md` for motivation. Current state: `GameService.createRound`
builds a solo world and starts its clock immediately; `RunningRound` fixes its
roster (1 human + 5 rivals + 3 institutions) in the constructor;
`RoundLifecycle` allows CREATED→WARMUP→RUNNING→… with no holding state.
`multiplayer-seats` (prerequisite, applied first) adds creation-declared extra
humans with join-order seats and per-seat purses.

## Goals / Non-Goals

**Goals:** browse, create, join and start rooms; solo flow byte-identical.

**Non-Goals:** late joins and takeovers (rooms lock — decided); matchmaking
UI beyond list + join; master transfer if the master leaves (master seat
simply stays theirs; abandonment settles with the round).

## Decisions

### D1. WAITING as a lifecycle state, clock held outside the world

**Decision:** Add `RoundState.WAITING` with edges CREATED→WAITING (rooms) and
WAITING→WARMUP (start); the solo path CREATED→WARMUP is untouched. Held
creation builds everything `createRound` builds today (warmup history, roster,
journal, tariffs, purse resets) but neither starts the clock nor the driver;
master start transitions and boots both.

**Rationale:** Warmup and history are deterministic functions of the seed, so
holding costs nothing and solo behavior cannot shift — the tick loop only ever
sees a started world.

### D2. Trim-at-join, never mid-round

**Decision:** Joins before start append the human and yield the last roster
rival (VULTURE first), keeping 6 ranked seats; climbing back is impossible.
Once the clock runs, roster and runners are immutable — joins are refused as
`started`.

**Rationale:** Pre-start nothing has ticked: no fills, no journal rows, no
marks — removal is total and safe. This keeps first-N backfill invariant at
every headcount without a placeholder-seat scheme.

**Alternatives:** declaring headcount at creation (forces masters to guess
turnout; empty seats would need backfill-by-default anyway — same code, worse
UX).

### D3. Master is the creator, start is creator-or-full

**Decision:** The creating player id is recorded as master; `rooms/start`
checks it, except a full room starts itself on the claiming join. No transfer,
no vote.

**Rationale:** One check, no new protocol; full-room autostart covers the
common case without a master round-trip.

### D4. Public listing, authed mutation

**Decision:** `POST /api/rounds/live` lists rooms (no auth — lobby needs it
pre-login? no: post-login, but any approved player); create/join/start require
the login token, join additionally requires approval + free seat.

**Rationale:** Listing is harmless world state; every mutation stays inside
the existing token realm, so no new auth machinery.

## Risks / Trade-offs

- **Held rounds accumulate** → abandoned waiting rooms linger; mitigate with a
  create-time expiry (unstarted rooms vanish after N minutes — cheap sweeper,
  part of implementation).
- **Two masters race full-room autostart vs manual start** → start is
  idempotent (second call is a no-op success), so the race is harmless.
- **Clock/tick consumers assume ticking** → resync/history on a waiting round
  return warmup state only; the terminal shows the waiting room until start.

## Migration Plan

Additive: new state, new endpoints, new lobby screen; solo creation path
delegates with one human and starts immediately (today's behavior verbatim).
Rollback is reverting; no migrations (rooms live in memory like rounds).

## Open Questions

- Room expiry duration (propose 30 minutes; confirm during implementation).
