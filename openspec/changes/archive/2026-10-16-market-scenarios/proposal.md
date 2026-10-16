# Proposal — Market scenarios (change: market-scenarios)

## Why

Playtest verdict: the loop is sound but the news is wallpaper — earnings
±1-4% on rotation, nothing a player retells. Wall $treet's pull was peculiar,
violent, staged events: takeover bids, scandals, crashes, squeezes, rumors
that resolve. Our engine already stages rumor → confirm/deny and halts on
10% bands, but the generator never deals anything above 0.04 magnitude, so
neither path ever fires.

## What Changes

A rare scenario deck in the news timeline (~2 per 30-min round, seeded,
never in the opening 10%, spaced apart). Each scenario is a staged
rumor → resolution arc using the existing `PlannedNews` machinery:

- **Takeover bid** (company, +0.30 rumor-half): approach at a fat premium;
  confirm completes it, deny walks it all back.
- **Accounting scandal** (company, −0.25): auditors won't sign; deny =
  cleared with partial recovery.
- **Flash crash** (macro, −0.08): rumor drops, deny snaps the V back.
- **Short squeeze** (company, +0.20, short validity): violent, fades fast.
- **Central-bank shock** (macro, ±0.10, long validity): the session's weather.
- **Meme stampede** (company, +0.15, short validity): violent, fades fast.
- **Fraud probe / CEO ouster** (company, −0.18): scandal variants.

Magnitudes are sized to trip REALISTIC 10% breakers, so halts actually occur
and the halt/reopen path gets exercised. Validity follows the existing
magnitude × scope formula (big shocks stay tradeable ~all game). Narrator
needs nothing new: scenario headlines carry canon subjects like earnings do.

## Capabilities

- `market-simulation` (delta: scenario deck).

## Impact

- `server/core` news (deck + planner wiring, seeded determinism untouched) +
  unit/soak tests. Runtime, edge, web untouched — scenarios ride the wire,
  ladder, halts and narrator that already exist.
