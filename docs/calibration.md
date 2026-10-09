# Calibration

This document records measurements produced by the calibration harness
(`server/core/src/main/java/com/virtualinvestor/core/calibration/Calibration.java`) and the
matching benchmark. Re-run them after any change to the fundamental or matching parameters.

## Fundamental model stylized facts

Run:

```bash
cd server && mvn -q -pl core compile && \
  java -cp core/target/classes com.virtualinvestor.core.calibration.Calibration
```

Configuration: default universe (20 equities + 1 index), seed `20261002`, step 100 ms,
market annual vol 0.30, sector annual vol 0.20, 200,000 steps.

Latest measured output:

```
steps=200000
marketReturnMean=-0.0000000250
marketReturnStd=0.0000502600
marketReturnSkew=-0.011424
marketReturnKurtosis=3.009530
marketReturnAutocorrLag1=-0.002923
averagePairwiseCorrelation=0.431462
annualizedMarketVol=0.385950
```

Interpretation:

- Mean return is effectively zero; skew is near zero and kurtosis is near 3, i.e. the
  diffusion-only process is close to Gaussian. Fat tails and jump kurtosis are expected once
  news events inject discrete fundamental jumps (Group 7).
- Lag-1 autocorrelation is ~0, as expected for a random-walk fundamental.
- Average pairwise correlation of ~0.43 reflects the shared market and sector factors.
- Realized annualized market vol (~0.39) exceeds the 0.30 input because the equal-weight
  market proxy carries idiosyncratic variance in addition to the market factor.

## Order-flow impact

Run:

```bash
cd server && mvn -q -pl core compile && \
  java -cp core/target/classes com.virtualinvestor.core.calibration.ImpactCalibration
```

Configuration: synthetic instrument, liquidity 1,000,000 shares (free float), a single impulse
of 100 shares of buy flow, permanent coefficient 5.0, transient 20.0, transient decay 0.97,
reversion 0.02. Coefficients are per unit flow divided by tradeable float.

Latest measured output:

```
peGapAfterImpulse=0.00250000
gapAfterSteps[1]=0.00195902
gapAfterSteps[5]=0.00180694
gapAfterSteps[20]=0.00133455
gapAfterSteps[100]=0.00026511
gapAfterSteps[500]=0.00000008
permanentCoefficient=5.000000
transientCoefficient=20.000000
transientDecay=0.9700
reversionStrength=0.0200
```

Interpretation: a 100-share aggressive order moves the fundamental ~0.25% at the moment of
impact, decays to ~0.027% within 100 steps, and leaves a ~0.05% permanent offset that survives
reversion. Impact scales inversely with liquidity, so an illiquid name moves more for the same
order. The permanent component moves the reference (so reversion preserves it); the transient
component moves only the fundamental (so it decays).

Live confirmation (market-microstructure 6.2, 2026-10-06): a 494-share market buy in PEAR
(~$90k) over the socket printed 182.01 → 183.13 (+0.6%) within seconds as bots reacted to
the moved fundamental, then settled at 182.16 (+0.08%) — transient decay with a permanent
remainder, exactly the modeled shape. (NVDO shows nothing at this size: at ~950k-share
liquidity a 155-share impulse is below the value bots' 0.5% reaction threshold — impact
needs an illiquid-enough name to surface in prints.)

## Matching throughput

Run:

```bash
cd server && mvn -q -pl core compile && \
  java -cp core/target/classes com.virtualinvestor.core.calibration.MatchingBenchmark
```

Configuration: 20 instruments, deep seeded book, 5,000,000 measured crossing limit orders
(200,000 warmup), event emission included.

Latest measured output (target machine, i5-5257U-class CPU):

```
instruments=20
measuredOps=5000000
elapsedMillis=774
matchingOpsPerSecond=6459948
matchingOpsPerSecondPerInstrument=322997
```

Interpretation: ~6.46M submissions/second aggregate, ~323k/second per instrument, including
order-lifecycle and trade-print event construction. This is far above what a 10 Hz world with
a handful of participants needs; the world clock, not matching, will be the limiting factor.

## Full-stack resource usage

Measured on the target machine (i5-5257U-class CPU, 8 GB RAM) on 2026-10-02.

Simulation core throughput (60,000 world steps, 21 instruments):

```
steps=60000 elapsed_ms=306
heap_used_mb=53
```

- ~5 microseconds per world step across 21 instruments (fundamental factor model
  only), so a 30-minute session at 10 Hz (18,000 steps) is well under a second of
  core compute.
- Heap ~53 MB with `-Xmx512m`; the core's memory footprint is dominated by
  instrument/position maps, not history.

Bot-only round test (full JVM + Maven + test suite invocation):

```
Maximum resident set size (kbytes): 204980   (~200 MB, includes Maven/JVM + tests)
Elapsed (wall clock): 0:06.00
```

The `~200 MB` figure is the whole Maven/JVM process for a full test run, not the
running simulation; the simulation itself is a small fraction of it. On this
machine the stack runs comfortably, and the earlier caution stands: avoid running
the full compose stack, local dev servers and a local LLM simultaneously.

## Broker fees (player-accounts 5.3)

Configuration: seed `42`, 180-minute session at minute steps (175 ticks),
tariffs round-robined over the seed schedules (0.005/1.00, 0.01/2.00,
0.02/5.00) by participant id. Measured 2026-10-04:

```
fees=false: rival-3 100869.66 | inst-1 100494.50 | rival-1 98393.62 | rival-2 98622.59
fees=true:  inst-3  100334.89 (fees 147) | rival-3 99614.23 (fees 1418)
            | rival-1 93666.46 (fees 3182) | rival-2 97799.35 (fees 835)
```

- The most active bot pays ~$3,200 over 175 ticks; idle participants pay nothing.
- Fee-aware equities reorder the ranking (inst-3 overtakes rival-3) — expected:
  leaderboard standing is now net of costs, which is the point of the tariff.
- Rerun-equality still holds (fees are a pure function of fills); absolute
  economics shifted, so earlier bot-economics snapshots no longer apply.

## Liquidity ladders (realistic-liquidity 3.2)

Configuration: seed `42`, 180-minute session at minute steps (175 ticks),
5-deep geometric ladders, per-maker toxicity sensitivity, tariffs as in the
fee baseline. Measured 2026-10-06:

```
orderEvents=7722 totalEvents=15948
rival-4 100643.38 (fees 154) | inst-3 100024.89 (fees 18) | rival-2 99535.86 (fees 335)
rival-3 98384.81 (fees 1328) | rival-1 95861.98 (fees 3076)
```

- Ladders + fade reorder economics again (rival-3 drops from the top; most
  active bot pays ~$3,100, slightly less than the $3,182 pre-ladder baseline
  as makers fade into toxicity).
- Controlled probe (identical fundamental path): 5-deep ladders emit 1.64x the
  order events of single-level quotes — under the 2x budget via quadratic tier
  cadences plus diffed reposts.

## Book replenishment (book-replenishment)

Configuration: seed `42`, 180-minute session at minute steps (180 ticks),
session-relative rung cadences, depth-aware takes, toxicity flow weight 500
(was 1000) and volatility weight 0.25 (was 1.0), tariffs round-robined over the
seed schedules (0.005/1.00, 0.01/2.00, 0.02/5.00) by roster order, plus a
player sweep into PEAR at tick 90. Measured from `MarketRealismTest`:

```
trades=3875 rejectedNoLiquidity=0
maxToxicity=0.508 on SOLR (gate floor 2.25, trips=0)
empty-side fraction across all symbols: 0.247
post-sweep PEAR ladder5: max 270 after the sweep, multi-level on 15 ticks
standings: MARK 105680.77 (fees 6431) | LEDGER 100707.87 (fees 1662)
  | ATLAS BANK 100699.45 (fees 647) | VULTURE 100000.00 (fees 0)
  | MERIDIAN CAPITAL 100000.00 (fees 0) | You 99040.92 (fees 3)
  | BLITZ 92508.09 (fees 1728) | CROWN FUND 91929.87 (fees 2335)
  | TIDE 87617.47 (fees 4470)
```

- Rejects went 1,412 → 0: depth-aware takes size to visible depth instead of
  firing into emptiness, while the trade count more than doubles (1,530 → 3,875)
  because takes now fill instead of rejecting.
- The volatility term carried roughly half of baseline toxicity (measured by
  zeroing it in a scratch run: means 0.22–0.53 → 0.09–0.31), benching CROWN/ATLAS
  on most symbols; at 0.25, ≥2 makers quote most of the time on 13 of 20 equities.
- Costs decide the table: the most active bot (TIDE, fees 4,470) finishes last
  while quiet LEDGER takes second — leaderboard standing is net of costs.
- Earlier bot-economics snapshots no longer apply; rerun `MarketRealismTest`
  for fresh numbers (deterministic on seed 42).

## News-reading bots (news-reading-bots)

Configuration: as above, plus per-symbol headline signals with per-archetype
delays (VULTURE 0 ticks, MERIDIAN 4) and ±25% seeded magnitude noise. Measured
from `MarketRealismTest`:

```
trades=3897 rejectedNoLiquidity=0
maxToxicity=0.528 on SOLR (gate floor 2.25, trips=0)
empty-side fraction across all symbols: 0.257
standings: MARK 106017.61 (fees 6465) | LEDGER 102637.02 (fees 1673)
  | ATLAS BANK 101866.49 (fees 645) | VULTURE 100035.11 (fees 70)
  | MERIDIAN CAPITAL 100000.00 (fees 0) | You 99107.60 (fees 3)
  | BLITZ 92746.15 (fees 1728) | CROWN FUND 91911.84 (fees 2335)
  | TIDE 87637.18 (fees 4480)
```

- VULTURE trades for the first time in any baseline (fees 70 on company
  earnings) — the headline hunter is no longer decorative.
- MERIDIAN stays flat here, correctly: the 180-tick seed carries no macro
  headlines (randoms need longer sessions). On a 30-minute session with 3 macro
  releases it took macro size three times (lifetime realized −43 — slow
  committees chase, and sometimes lose).
- Everything else barely moved (trades 3875 → 3897, empty fraction 0.247 →
  0.257, max toxicity 0.508 → 0.528): waking two bots perturbs the market
  without reordering it, so no watch bound moved.
- Delay/noise calibration (4 ticks, ±25%) is initial: confirmed harmless by
  the numbers above, to be revisited if news-day toxicity ever nears the gate.
