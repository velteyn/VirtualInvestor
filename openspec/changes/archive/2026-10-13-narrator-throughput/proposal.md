# Proposal — Narrator throughput (change: narrator-throughput)

## Why

Measured on the playtest box (4 cores, Qwen2.5-3B Q4): 12-30s per article at
`n_predict: 160`, scaling with tokens actually generated (~4-5 tok/s). A
30-min round selects ~30 releases for one FIFO desk thread → ~15-25 min of
queue, so late articles land stale ("priced in") or never. The 60-token cap
was tried: text cuts mid-sentence. The 100-token band reads complete at
~15-19s.

## What Changes

- Generation cost: `n_predict` 160 → 100, prompt asks 2-4 sentences / 80
  words (≈15-20s/article, ~2x throughput). No spec pins length.
- Shed stale work: the desk tracks the round's current tick and skips
  releases whose validity window already closed — narrating priced-in stories
  is pure waste, and validity-weighting auto-prioritizes earnings/macro over
  noise (long windows survive, short ones shed).
- Observability: per-article elapsed + outcome (published/declined/stale) in
  the log, so the next playtest verifies instead of guessing.
- **NOT in scope**: parallel desk/slots (CPU-bound, no gain on this box),
  fewer selections, model change.

## Capabilities

- `newsroom-narrator` (delta: stale shed + tighter articles).

## Impact

- `server/narrator` (constant + prompt + tests), `server/edge`
  (`NewsroomService` tick map/shed/logging, `RoundEvents` tick feed + tests).
- Live redeploy + round observation.
