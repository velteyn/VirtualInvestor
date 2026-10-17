# Design — Narrator story arcs

## Context

`PlannedNews` is one rumor + one resolution. Arcs need N beats with memory;
the narrator needs the past and the bible. Everything still rides `NewsItem`
→ `NewsEngine` → wire → narrator, so runtime/edge/wire stay untouched.

## Decisions

### D1 — Beats as chained PlannedNews with a story ID

`NewsItem.id` already namespaces (`scen-takeover-0`); arcs add a `storyId`
(stable across the arc's beats: `scen-takeover-<roundslot>`) plus beat index
in the body/headline pipeline. Resolution of beat N is the rumor of beat N+1
in market terms (continuous fundamental path, no jumps back), with the final
beat resolving like today (confirm/deny). Magnitudes per beat sum to the arc
total so the full move matches the deck band.

### D2 — Memory assembled edge-side at narration time

The desk already reads articles per round; per beat it loads the arc's prior
prior headlines (capped ~40 words — full bodies parrot back and bury the lead; prompt eval runs ~0.2s/token on
this CPU, so memory stays inside the generation timeout) plus consequences computed from the
round (price move % since last beat, halt flag via breakers). No prompt
change for non-arc news. Memory misses (pruned/old rounds) degrade to today's
single-shot prompt.

### D3 — Bible as deck data, injected verbatim

`ScenarioDeck.Entry` gains a `bible` block (≤40 words of canon facts).
Injected into the prompt as "You may use these facts:" — the denylist still
screens the output, and anything not in headline/subjects/bible that looks
like a new name or figure declines the draft. Pilot bible: takeover only.

## Risks

- Prompt length per beat grows (memory + bible) → slower CPU generations;
  bounded by truncation, and beats are rarer than releases (arcs ≤ 3/round).
- 3B coherence over 5 beats is unproven — hence pilot-first (task 2.2 gates
  section 3).

### D4 — Protection lane (2.3)

Single desk thread behind a `PriorityBlockingQueue`: lane 0 (any `scen-`
news id) ahead of lane 1, FIFO sequence within a lane. One thread stays —
generations are CPU-bound, a second worker would slow both. Arc beats are
exempt from stale-shed (a late story beats a dead one) and get the 120s
fuse (default raised from 60s; `VI_NARRATOR_TIMEOUT_MS` still overrides).
Decline reasons log at the narrator (`blank/status/empty/denylist/
degenerate/exception`), so the waste rate is diagnosable per release.
