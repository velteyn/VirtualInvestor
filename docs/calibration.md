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

To be recorded once the server and terminal are wired (task 12.5).
