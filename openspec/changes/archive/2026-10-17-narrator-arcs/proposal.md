# Proposal — Narrator story arcs (change: narrator-arcs)

## Why

The narrator is a headline colorist: one headline in, 2-4 sentences out, no
memory, no market state. Multi-beat stories (incident → fallout → response →
resolution) read as disconnected one-shots, and anything beyond the headline
is either forbidden (grounding) or hallucinated. Wall $treet's pull was
developing stories a player follows for days. The engine already stages
2-beat rumor → resolution arcs; the narrator needs memory, consequences, and
a canon source for specifics.

## What Changes

- **Chained beats**: scenario arcs extend past rumor → resolution to 3-5
  beats linked by a story ID (e.g., takeover: approach → bidding war →
  board decision → close/collapse). Engine stages them; each beat is its own
  wire event as today.
- **Memory in the prompt**: each beat carries the story-so-far (prior
  prior headlines plus market consequences since the
  last beat (price move %, halt status), so part three reads like part three.
- **Scenario bible**: per-scenario authored facts the model may use (named
  facilities, executives, figures) — invention becomes canon because the
  deck wrote it. Grounded against the denylist as today; bible facts bypass
  only the names/figures rules they explicitly state.
- **Pilot, then machinery**: one arc first (takeover bid), judged on the
  wire; generalize to the deck only if the pilot reads coherent.
- **NOT in scope**: new universe companies (oil-rig stories wait on an
  energy-sector roster decision), model swap, longer outputs per beat
  (memory grows the prompt already; bodies stay tight).

## Capabilities

- `newsroom-narrator` (delta: arcs + bible + memory).

## Impact

- `server/core` news (beat chaining, bible content), `server/narrator`
  (memory prompt, bible injection) + unit tests; a live pilot round judged by
  reading. Runtime/edge/wire unchanged (beats ride existing events).
- Plus, found by the first pilot: `server/edge` (priority lane, shed
  exemption, decline reasons, `scen-` selection), `server/persist` (V10
  `SCENARIO` article kind + migration).
