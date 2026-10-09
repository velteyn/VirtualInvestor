# Proposal

## Why

The `news-events` spec requires the news-driven rival to trade headlines per its declared strategy, but VULTURE (`NewsHunterStrategy`) and MERIDIAN (`MacroReactionStrategy`) never see one: the runner builds their context with the news signal hardcoded to `null`. Both sit out every session — flat 100k equity, zero fees in every economics baseline — while momentum and value bots trade only the aftermath. Wiring the handoff fulfills the spec and gives news days genuine informed flow.

## What Changes

- The world folds each tick's releases into per-symbol news signals and hands them to strategy contexts, so headline strategies fire on the release tick.
- Scope-routed delivery: company news reaches runners on that symbol, sector news its symbols, macro news the index only — so one headline can never trigger 21 engines at once.
- No strategy logic changes: VULTURE pounces above its threshold, MERIDIAN takes macro size, everyone else ignores the signal exactly as their code already does.
- Bot economics re-baseline (two bots wake up and start paying fees).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `news-events`: ADDED delivery requirement — signals reach strategies on release, scoped and deterministic. The existing "Differentiated bot reactions" requirement is unchanged; this change finally satisfies it.

## Impact

- `server/runtime`: `RunningRound` folds release events into signals, `BotRunner` carries them into `BotContext` (replacing the `null`).
- `server/core`: untouched (strategies, engine, and `NewsReleased` already carry everything).
- Economics: VULTURE (~40 shares per headline) and MERIDIAN (1,000 per macro event) start trading — standings, fees, toxicity and watch bounds all move; re-baseline in `docs/calibration.md`.
- No wire-protocol, persistence, or web changes. Determinism preserved (pure fold of the release events, same tick).
