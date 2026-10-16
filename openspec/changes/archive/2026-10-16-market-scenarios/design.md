# Design — Market scenarios

## Context

`NewsGenerator.generate` builds earnings + uniform noise; `PlannedNews`
(item, resolutionTick, confirmed, rumorFraction 0.5) already stages everything
`NewsEngine` needs. This change adds a third timeline source with its own
pacing, reusing both.

## Decisions

### D1 — Deck as data, planner as function

`ScenarioDeck`: one entry per scenario (scope, magnitude band, rumor
fraction, resolution delay band, confirm bias, headline templates with
`{name}`/`{sector}` slots). `planScenarios(random, universe, sessionTicks)`
returns `PlannedNews` list: count = 1 + floor(draw × 3) capped at 3 (so 1-3;
rounds always have at least one story to retell), start ≥ 10% session,
spacing ≥ 15% apart. Deterministic in the `news.random` stream like the rest.

### D2 — Magnitudes trip breakers on purpose

Bands: takeover +0.28-0.35, scandal −0.22-0.28, crash −0.08, squeeze +0.18-0.22,
bank ±0.09-0.12, meme +0.13-0.17, probe −0.15-0.20. Company shocks ≥0.10 trip
REALISTIC 10% bands (halt path exercised); macro ±0.10 moves the index and
every book at once. Validity via the existing formula (big = long).

### D3 — Confirm bias per scenario, deny always possible

Takeover confirms 65%, scandal confirms (guilty) 70%, crash denies
(V-bounce) 75%, squeeze/meme always "confirm" (no denial — the move just
fades via short validity), bank confirms 80%. Deny reverses the rumor piece
through the existing path; no engine change.

## Risks

- A 0.35 rumor can gap a book past makers' ladders → thin-book slippage.
  Accepted: that IS the drama; breakers and margin floors bound the damage.
- Earnings + scenario on one name same tick: allowed, ordered by tick then
  type in `NewsEngine` (existing loop order); combined moves are a feature.
