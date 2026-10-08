# Player accounts

Identity, banks with broker fees, and the game-master backyard. All REST calls
below need `X-API-Version: 1`; admin calls additionally need `X-Admin-Token`.

## Playing as a registered player

```bash
# 1. register (goes PENDING; pick a bank from POST /api/players/banks)
curl -X POST localhost:8080/api/players/register \
  -H 'Content-Type: application/json' -H 'X-API-Version: 1' \
  -d '{"username":"ana","password":"secret-1","bankId":"meridian"}'

# 2. log in once approved (pending logins get 403)
curl -X POST localhost:8080/api/players/login \
  -H 'Content-Type: application/json' -H 'X-API-Version: 1' \
  -d '{"username":"ana","password":"secret-1"}'
# -> {"playerId":"…","status":"APPROVED","bankId":"meridian","sessionToken":"…"}

# 3. create a round bound to the purse, join the socket with the token
curl -X POST localhost:8080/api/rounds \
  -H 'Content-Type: application/json' -H 'X-API-Version: 1' \
  -d '{"startingCapital":100000,"durationMinutes":15,"preset":"realistic","sessionToken":"…"}'
# -> {"roundId":"round-1",…}
# ws: {"type":"joinRound","roundId":"round-1","participantId":"human","sessionToken":"…"}
```

Sockets without an approved login token are refused with `not-approved` and can
only watch. In the browser the launch pad handles register/login/pending; the
terminal joins with the stored token automatically.

## Banks and fees

| bank | display | per share | ticket min | custody |
|---|---|---|---|---|
| `first-ledger` | First Ledger Bank | 0.005 | 1.00 | — |
| `meridian` | Meridian Trust | 0.01 | 2.00 | 0.25% |
| `apex` | Apex Private | 0.02 | 5.00 | 0.5% |

Every fill deducts `max(ticket minimum, per-share x filled quantity)` from each
counterparty's cash — maker and taker pay their own bank's schedule, bots
included, so the leaderboard stays fair. Totals surface as `feesPaid` on the
account frame and feed the portfolio tab. Custody is published but not billed
yet.

## Purse

Cash + holdings live in the database, independent of rounds. Creating a round
resets the purse to the round's starting capital (casual daily-reset policy);
settlement writes the result back. A later career mode reads the stored purse
instead of resetting.

## Game-master backyard

Start the edge with a master password (empty = admin login impossible):

```bash
VI_ADMIN_PASSWORD="…" java … com.virtualinvestor.edge.EdgeApplication
```

Open `/admin.html` (same origin as the terminal) and log in, or call the API:

```bash
# login -> {"adminToken":"…"} (send as X-Admin-Token from here on)
curl -X POST localhost:8080/api/admin/login \
  -H 'Content-Type: application/json' -H 'X-API-Version: 1' \
  -d '{"password":"…"}'

curl -X POST localhost:8080/api/admin/players -H 'X-Admin-Token: …' … -d '{}'
curl -X POST localhost:8080/api/admin/players/approve -H 'X-Admin-Token: …' … \
  -d '{"playerId":"…"}'            # also: reject, reset-password, reset-purse
curl -X POST localhost:8080/api/admin/rounds/settle -H 'X-Admin-Token: …' … \
  -d '{"roundId":"round-1"}'       # also: pause, resume
```

Player login tokens never authorize admin calls and admin tokens never join
rounds — the realms reject each other with 401 / `not-approved`. There is no
email and no self-service recovery: a locked-out player asks the game master
for a password reset, which invalidates their old sessions.

## Shared rounds and seats

A round seats up to 6 ranked humans: the first takes `human`, later ones
`human-2` and so on in join order, each with its own purse reset and
write-back. Unclaimed seats backfill first-N by roster order (one human plays
exactly the classic game: you plus MARK, TIDE, LEDGER, BLITZ, VULTURE); banks
assign by bot name so behavior never shifts with headcount. The 3 unranked
institutions always make markets.

Joining binds the login token's seat — the stated participant id is ignored —
and sockets without a seat get `not-approved`. Rooms lock at start (no late
joins; that gate arrives with the lobby): Ana creates and starts with Bo
aboard while Carlo, arriving after the bell, waits or starts his own room.
Each seat settles to its own purse and ranks on the shared board.
