# Liquidity

How the book gets its shape, when size hurts, and how makers react when flow turns
toxic. Implementation: `MarketMakerStrategy` + `BotRunner` (core/runtime), the toxicity
fold `FlowToxicity`, markouts in `Markout`, read endpoint `POST /api/rounds/markouts`.
End-to-end proof: `LiquidityEndToEndTest` (realistic-liquidity 4.1).

## The ladder

Makers (MARK, ATLAS, CROWN) quote 5 levels per side on every symbol: small at the touch,
larger behind (each rung ~2x the previous). Levels step outward in half-spreads from a
fundamental-anchored mid; the base half-spread is 0.02 and widens with volatility, and the
mid skews against inventory. Sizes scale with the maker's equity: roughly 2% of equity per
rung unit, so a $100k maker posts about `2000 / price` shares at the touch:

| Symbol | Ref price | Touch per maker | 3-maker touch | Full 5-deep book |
|---|---|---|---|---|
| SOLR | ~$64 | ~31 | ~90 | ~2,900 |
| NVDO | ~$641 | ~3 | ~9 | ~280 |

(The book totals assume calm; stress shrinks them — see below.)

## When size hurts

A market order that fits inside the touch fills at one price, exactly as before. Past the
touch it **walks**: one print per level crossed, each worse than the last. The taker's own
`order` frame is cumulative (one `FILL` at the last price); the walk is visible in the
`trade` tape and in per-level `depth` frames. Past the whole visible book the remainder is
cancelled (`PARTIAL` + `"market remainder cancelled"`); the book is then empty until makers
replenish on their refresh cadence (touch every 50 ticks, deeper tiers slower).

Rule of thumb: below the 3-maker touch you pay no spread beyond the quote; up to the full
book you pay a measured walk; beyond it you sweep the book and move on to an empty one.

## Stress: fade, then withdrawal

Each symbol carries a toxicity score: one-sided aggressive flow and elevated volatility
raise it, calm decays it (×0.97 per tick). One 200-share SOLR take adds ~0.24; the score is
liquidity-scaled, so the same order scares an illiquid name more. Per maker:

- size × `max(0, 1 − sensitivity × toxicity)`, spread × `(1 + sensitivity × toxicity)`;
- sensitivities: MARK 1.0, ATLAS 3.5, CROWN 4.5 — so CROWN's rungs hit zero first
  (toxicity ~0.22), then ATLAS (~0.29), then MARK (~1.0);
- a tolerance backstop (4.0 / 2.75 / 2.25) empties the quote outright.

Sizes shrink first, spreads widen second, quotes vanish last — and makers never flinch in
lockstep, so books thin unevenly instead of flashing empty everywhere. When stress fades,
makers requote on their next refresh.

An empty side rejects market takes with reason `"no liquidity for market order"` (distinct
from `"instrument is halted"` during a breaker trip and from validation rejects). The
terminal surfaces the reason on the order line.

## Refresh discipline

Reposting is diffed per level: a rung whose price/size is unchanged stays resting and emits
nothing. The touch refreshes every 50 ticks, deeper tiers on quadratic multiples, and each
maker's phase offsets by roster index — so a 5-deep ladder costs ~1.64x the order events
of single-level quotes (measured in `docs/calibration.md`), not 5x.

## Markouts

Every fill is joined, off the hot path, to the first print on its symbol at or past fill
tick + horizon (default 5 ticks): `fillPrice`, `markPrice`, and the signed `markout`
(positive = price moved in the taker's favour). A fill with no later print resolves to
nothing and is skipped. Read them via `POST /api/rounds/markouts`; calibration consumes
the same fold.

## Tolerance backstop: a safety net, not a gameplay path

The tolerance backstop (2.25–4.0) is deliberately unreachable through organic flow: the
deepest sweep the book affords spikes toxicity to ~0.5, well short of the 2.25 floor, and
the per-tick decay erases it between refresh waves. In live markets withdrawal happens
through the fade-to-zero path plus consumption, never through the tolerance gate — and that is the
desired behavior: genuine all-makers-out-at-once withdrawal is a flash-empty book where
every market order rejects, which is a failure mode, not a feature. The gate exists as a
last-resort safety net (unit-tested with synthetic toxicity), the same way a circuit
breaker exists without being expected to trip on an ordinary day. If a future change
needs genuine tolerance-gated withdrawal, recalibrate first: slower decay, lower
tolerances, or deeper ladders — and re-baseline bot economics as in
`docs/calibration.md`.

## Market watch

`MarketRealismTest` (`server/runtime`) replays a full 180-minute session on the
economics-baseline seed (42), fires a large player sweep into PEAR mid-session, and
asserts the market behaves: the sweep executes, the toxicity sensor moves, the gate
never trips, rejects for no liquidity stay under 0.5% of trades, books sit empty at
most 30% of symbol-ticks, and ≥2 makers quote most of the time on most equities.
Deterministic: same seed, same report. Re-run after any change to makers, takers, or
toxicity, and read the printed `MARKET WATCH` lines in the surefire output:

```bash
cd server && mvn -pl runtime test -Dtest=MarketRealismTest
```

Current calibration (book-replenishment): toxicity flow weight 500 (was 1000),
volatility weight 0.25 (was 1.0). The volatility term turned out to carry roughly half
of baseline toxicity, benching CROWN/ATLAS on most symbols; halving the flow weight
alone barely moved the means (19-share-equivalent feedback: more depth invites more
flow). Sweeps still thin the book (509 → 0 resting) while the gate stays holstered.
