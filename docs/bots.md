# Bots and institutions

This document lists the virtual traders in a round: the five named rivals the player competes
against on the leaderboard, and the institutions that act as the market. Every bot is a
`BotParticipant` (or, for the market maker, a liquidity provider) driven by a strategy class
in `com.virtualinvestor.core.bot`.

## The five rivals (visible on the leaderboard)

| Name | Role | Strategy class | Behaviour |
|---|---|---|---|
| **MARK** | Market maker | `MarketMakerStrategy` | Quotes two-sided prices around the fundamental; spread widens with volatility and the mid skews against inventory. |
| **TIDE** | Momentum | `MomentumStrategy` | Buys when recent price action is up, sells when down, amplifying moves. |
| **LEDGER** | Value | `ValueStrategy` | Buys below the fundamental by the entry edge, sells above it, pulling price back toward fair value. |
| **BLITZ** | Scalper | `ScalperStrategy` | Fades short-term moves, selling a pop and buying a dip. |
| **VULTURE** | News hunter | `NewsHunterStrategy` | Pounces on a headline in the direction of its magnitude. |

All five are ranked by mark-to-market equity and seeded with the player's starting capital
(spec: fair ranking).

## Institutions (the market, never ranked)

Institutions provide liquidity and react to news and macro conditions. They are
`ParticipantType.INSTITUTION` and are excluded from the leaderboard by `Leaderboard.ranked`.

| Name | Role | Strategy class | Behaviour |
|---|---|---|---|
| **ATLAS BANK** | Block execution | `VwapExecution` | Works a large parent order in deterministic slices to limit market impact. |
| **MERIDIAN CAPITAL** | Macro reaction | `MacroReactionStrategy` | Trades macro-scope headlines with size, in the news direction. |
| **CROWN FUND** | Index / basket flow | `VwapExecution` (as a basket) | Index-like flow worked over time. A dedicated index-rebalance strategy is future work. |

Institutions can be beaten indirectly: front-running a large VWAP print or leaning against a
macro reaction is a legitimate player strategy.

## Not yet implemented

- Full roster wiring into a running round (Group 8).
- CROWN FUND's index-rebalance behaviour as a distinct strategy.
- Bot-specific capital, risk limits, and per-personality sizing beyond the fixed
  `orderQuantity` each strategy is constructed with.
