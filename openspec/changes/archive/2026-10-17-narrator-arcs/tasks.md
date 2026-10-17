# Tasks — Narrator story arcs

## 1. Beat machinery

- [x] 1.1 Chain beats by story ID in the planner (3-5 beats, per-beat
  magnitude fractions summing to the arc total, resolutions between beats).
- [x] 1.2 Prompt carries story-so-far (prior beats, truncated) + market
  consequences (move %, halt) per beat.

## 2. Bible + pilot

- [x] 2.1 Takeover bible (bidder stance, named advisors as canon
  facts); bible injection with denylist still enforced around it.
- [x] 2.2 Pilot round: takeover arc judged end-to-end (NVDO 3-beat arc
  fully narrated: bible used, consequences used, no leaked instructions or
  figures after v2 prompt; residual 3B looping tails trimmed by dedupe).
- [x] 2.3 Protection lane: arc beats jump the queue, are exempt from
  stale-shed, and get a longer fuse (timeout default 60s→120s), so slow
  important work is never killed. Decline reasons logged.
- [x] 2.4 Scenario-kind selection: `scen-` beats map to a new `SCENARIO`
  article kind (V10 migration) — without it the filter dropped every beat
  silently, which is why the first pilot narrated nothing.

## 3. Generalize (only if the pilot reads coherent)

- [x] 3.1 Bibles + beat plans for the remaining scenarios; suites +
  `openspec validate` green. (All six bibles in, denylist-safe by test;
  bible path proven live by the takeover pilot.)
