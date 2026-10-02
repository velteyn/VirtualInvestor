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
                         |  ^
                         v  |
                       PAUSED
```

- `CREATED → WARMUP → RUNNING → CLOSING → SETTLED`
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

## Not yet implemented

- Persistence, reconnect and save/resume of a running round (Group 9).
- Wiring the lifecycle, warm-up, sessions, news, bots and settlement into a single running
  `World` (Group 10 and beyond).
