# Proposal

## Why

Every headline currently has the same temporal shape: one-shot signal, permanent value, decaying heat. Real news doesn't work that way — an earnings beat burns out in days while a supply shock reprices a sector for weeks — so all of our bots effectively share one attention span. Per-news validity windows give fast money and slow money different markets to inhabit.

## What Changes

- Every news signal carries a validity window (`releaseTick + durationTicks`, reusing the dormant field): bots whose perception delay hasn't elapsed when the window closes never react — as if never read.
- Headline-chasing expires; gap-trading doesn't. Price-based strategies (value, momentum, maker toxicity/volatility) never see expiry — they read prices, not newspapers — so drift and fade dynamics continue untouched.
- Generator rule: validity scales with scope × magnitude (earnings hundreds of ticks, noise shorter, Hormuz-class shocks thousands), confirmed against the market watch.
- Newsroom shows "priced in" on expired items; the archive stays causally true because fundamentals permanently absorb every move.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `news-events`: ADDED validity requirement — signals expire for headline reactions while absorbed value persists. Existing reaction, staging, and volatility requirements unchanged.

## Impact

- `server/runtime`: one-clause visibility check (`tick <= release + validity`) composed with the existing mailbox delay; generator validity rule.
- `server/core`: untouched (fields and staging already carry everything).
- `web`: newsroom "priced in" states (only if the newsroom tab exists by then; otherwise the tag rides the existing news frames).
- Economics: slow money systematically misses fast news — expect small flow shifts, re-baseline as usual. Determinism preserved (window is a pure function of timeline + tick).
