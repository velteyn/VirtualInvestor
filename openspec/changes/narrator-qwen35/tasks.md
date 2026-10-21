# Tasks — Narrator Qwen3.5

## 1. Trial load

- [x] 1.1 Weights on disk (Hob-forge Q4_K_M) with size check.
- [x] 1.2 Sidecar boots the arch (build 11515 loads it, no image upgrade).

## Verdict: REJECTED for this box (2026-10-16)

Generation runs at **0.36 tok/s** (5-token prompt: 6.5s eval; 20 tokens:
56s) — ~12x slower than Qwen2.5-3B on the same CPU. The hybrid
linear-attention layers have no fast CPU path in this build; a 100-token
article would take ~5 minutes. Raw-prompt output also looked degenerate
(`A: OK. A: 12345678.`). Trial container removed, weights deleted
(re-download: `Hob-forge/Qwen3.5-4B-Instruct-GGUF`,
`Qwen3.5-4B-Instruct-Q4_K_M.gguf`). The whole 3.5-small series shares the
hybrid arch, so the 2B is rejected on the same grounds without a trial.
Staying on Qwen2.5-3B-Instruct.

## 2. Thinking + A/B

- [ ] 2.1 Not pursued — rejected at trial load.
- [ ] 2.2 Not pursued — rejected at trial load.

## 3. Integration

- [ ] 3.1 Not pursued — rejected at trial load.
