# Proposal — Narrator Qwen3.5 (change: narrator-qwen35)

## Why

The newsroom runs Qwen2.5-3B-Instruct Q4_K_M (~4-5 tok/s on the playtest
box). Qwen3.5-4B-Instruct (dense, Apache 2.0, March 2026) is the same weight
class (~2.6 GB Q4_K_M) with materially better instruction-following, which is
exactly our failure mode: declined articles waste ~17s each, and weak rule
adherence needed the no-company grounding patch. Same footprint, likely fewer
wasted generations.

## What Changes

- Trial, sidecar-only: download Hob-forge Q4_K_M (measured, imatrix), point
  the narrator container at it, keep the 2.5 weights on disk.
- Compatibility gates, in order: sidecar loads the arch (build 11515 vs
  b91xx-era quants — verify, else upgrade the image); thinking blocks
  stripped (model reasons by default; server-side `--reasoning-format none`
  if supported, else client-side strip in `LlamaCppNarrator`); smoke prompt
  returns clean short prose.
- A/B on one live round each: article latency, decline rate, grounding
  (names/figures). Keep the winner; the loser stays on disk, config flips.
- **NOT in scope**: chat-template migration (`/completion` stays unless the
  trial forces it), parallel slots, model larger than 4B (this box).

## Capabilities

- `newsroom-narrator` (delta: model record + thinking-strip rule if needed).

## Impact

- `infra/narrator-models/` (+2.6 GB), compose `MODEL_FILE`, maybe
  `entrypoint.sh` (reasoning flag) and `LlamaCppNarrator` (strip); narrator
  unit tests if code changes. No game-logic touch.
