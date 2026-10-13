# Design — Narrator throughput

## Context

See `proposal.md` for the benchmark (60/100/160 tokens × 2: 12-15s, 14-19s,
16-31s; 60 cuts mid-sentence). Box: 4 cores, 7GB — model and params stay.

## Decisions

### D1 — Cap 100, prompt 80 words

`N_PREDICT` constant (was inline 160); prompt "2 to 4 vivid sentences, at
most 80 words". Cost follows generated tokens (~4-5 tok/s), so natural early
stops also speed up. Stub-server test captures the payload and asserts the
cap; prompt test asserts the 80-word ask.

### D2 — Tick-gated shed in NewsroomService

`Map<String, Long> roundTicks` fed by `noteTick(roundId, tick)` from
`RoundEvents` on `GameEvent.Tick`. `write()` sheds when
`isStale(releaseTick, durationTicks, nowTick)` (pure static, unit-tested):
unknown tick (old rounds) → never shed. Previews/recaps exempt (no window).
Skips logged with release id + lag.

### D3 — Elapsed + outcome per article

`write()` logs `newsroom article <id> published in <ms>` /
`declined` / `stale-skipped`. INFO: ~30 lines/round, the only window into
desk health (playtest: silent wedge).

## Risks

- Shorter bodies may read thinner — accepted; headlines carry facts regardless.
- Shedding narrows late-round coverage of expired noise — intended.
