# Game session

How a round is configured, started, played and settled. Implementation lives in
`com.virtualinvestor.core.game` and `com.virtualinvestor.core.participant`.

## Launch pad

`LaunchConfig` is what the player chooses before a round starts:

- **starting capital** — within `[10,000, 5,000,000]`;
- **duration** — within `[15, 240]` minutes, converted to world ticks by `durationTicks(stepMillis)`;
- **realism preset** — `ARCADE`, `REALISTIC` or `HARDCORE` (see `docs/market-rules.md`).

An out-of-range value is refused with an explicit reason.

## Round lifecycle

`RoundLifecycle` guards the legal states:

```
CREATED --> WARMUP --> RUNNING --> CLOSING --> SETTLED
   |                       |  ^
   |                       v  |
   +--> WAITING --> WARMUP PAUSED
```

- `CREATED → WARMUP → RUNNING → CLOSING → SETTLED` (solo sessions skip waiting)
- `CREATED → WAITING → WARMUP` (rooms: the clock is held until master start)
- `RUNNING ↔ PAUSED`
- any other transition is rejected.

A round is identified by its id, seed and `LaunchConfig`; `signature()` captures all three so
the round can be reconstructed.

## Warm-up

Before trading opens, `WarmupGenerator`:

- generates OHLCV history from the fundamental process so charts and indicators have data on
  tick one;
- seeds every instrument's book with a two-sided market-maker quote so the book is never empty
  at the open.

## Participants

`RivalRoster` creates the cast: five named rivals (MARK, TIDE, LEDGER, BLITZ, VULTURE) and
three institutions (ATLAS BANK, MERIDIAN CAPITAL, CROWN FUND). Rivals are seeded with the
player's starting capital so absolute-equity ranking is fair.

## Session phases

`SessionStateMachine` maps the world clock to `PRE_OPEN → OPENING_AUCTION → CONTINUOUS →
CLOSING_AUCTION → CLOSED`, driving `AuctionOpened`/`AuctionClosed` and `SessionOpened`/
`SessionClosed`. See `market-rules.md` for the auction mechanics.

## Margin and settlement

- `MarginManager` checks equity against the maintenance fraction of position value each tick;
  a margin call emits `MarginCallIssued` and liquidates every position at its mark, emitting
  `LiquidationExecuted`.
- At the closing bell, `Settlement.settle` marks all positions to the close, force-closes every
  position (pure intraday — nobody carries overnight), computes final equity, and emits
  `RoundSettled`.

## Ranking

`Ranking.standings` orders participants by mark-to-market equity, reports return on starting
capital, and excludes institutions. See `docs/bots.md` for the cast.

## Lobby and rooms

After login the lobby (`POST /api/rounds/live`, public) lists every live game —
solo sessions and rooms — with kind, state, preset, time left, seats taken of 6
(rooms) or 1 (solo), and the current leader.

- **Solo**: `POST /api/rounds` builds a private 1-human world and starts its
  clock at once, exactly as before.
- **Create room**: `POST /api/rooms` (login token required) builds the world
  without starting its clock and holds it in `WAITING`; the creator is room
  master on seat `human`.
- **Join**: `POST /api/rooms/join` claims the next ranked seat (`human-2`, …)
  in join order; each join yields the last rival bot, so the ranked roster stays
  six. Joins to live rooms refuse as `started` (409), joins with no free seat as
  `full` (409); rejoining a seated player returns their seat.
- **Start**: `POST /api/rooms/start` boots the clock. Only the master can start
  a waiting room (strangers get 403); a room that fills to 6 humans starts
  itself on the claiming join; starting a running room succeeds silently.
- **Lock**: once the clock runs the roster is immutable — no late joins, no
  bot-seat takeovers. Settled rooms stay listed with no Join button.
- **Expiry**: rooms that never start vanish 30 minutes after creation
  (`ROOM_TTL_MILLIS`); held worlds never ticked, so expiry leaves no journal
  rows, no driver, no lobby row.

Client order tokens are unique per sender intent (`tk-<millis>-<seq>`): the
server dedupes by token per round, so two players (or a page reload) reusing
bare sequence numbers would silently drop each other's orders.

### Multiplayer walkthrough (reproduces as written)

Server on `:8080` (`VI_ADMIN_PASSWORD` set), web on `:5173`, three approved
players (Ana master, Bo, Carlo) plus Dan for the late knock:

1. Ana logs in, creates a room → waiting 1/6, Start visible (master).
2. Bo and Carlo log in, join from the lobby → waiting 3/6, no Start button.
3. Leaderboard shows Ana, Bo, Carlo plus MARK/TIDE/LEDGER — VULTURE and BLITZ
   yielded, institutions unranked as always.
4. Ana starts → all three refresh into the terminal (RUNNING).
5. Past the pre-open + auction phases (≈7% of session ticks), Ana buys 5 NVDO
   and Bo buys 3 NVDO → each has fills in `POST /api/rounds/fills`.
6. Game-master settles early (`POST /api/admin/rounds/settle`) → final
   standings exist and purses write back.
7. Dan joins the live room → 409 started, stays in the lobby; after settle the
   room lists with no Join button.

## Not yet implemented

- Persistence, reconnect and save/resume of a running round (Group 9).
- Wiring the lifecycle, warm-up, sessions, news, bots and settlement into a single running
  `World` (Group 10 and beyond).
