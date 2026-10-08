# Proposal

## Why

The full-market watch (`MarketRealismTest`, seed 42) shows books that wound but never heal: after a mid-session sweep PEAR's ask goes 464 → 0 and is still 0 at the close, empty 108 of ~176 continuous ticks, and 1,412 aggressive takes reject with "no liquidity" against 1,530 trades. Two structural causes: outer ladder rungs refresh on 200/450-tick cadences that never fire inside a 180-tick session, and takers fire market orders without checking resting depth. A market where half of all takes fail is not realistic.

## What Changes

- Replenishment cadence fits the session: every ladder rung requotes at least a few times per session while keeping the <2x event-traffic budget (retune tiers, not just the touch).
- Depth-aware taking: bot strategies check resting depth (and spread) before sending market orders, sizing down or standing aside when the book is thin instead of burning rejects.
- Fade calibration vs baseline flow: mean toxicity under ordinary bot flow (0.23–0.58 on 19/20 symbols) currently benches CROWN (0.22) and ATLAS (0.29) nearly permanently; sensitivities, tolerances, or the flow weight move so baseline flow keeps at least two makers quoting on most symbols.
- The tolerance backstop stays a safety net: gate trips remain zero in live sessions.
- `MarketRealismTest` gains teeth: reject-rate and empty-book telemetry become assertions with calibrated bounds, so the watch fails when realism drifts.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `realistic-liquidity`: replenishment becomes a requirement (rungs rebuild in-session), taking becomes depth-aware, fade points are calibrated against measured baseline flow. New ADDED requirements; existing fade/gate/refresh requirements unchanged.

## Impact

- `server/runtime`: `BotRunner` refresh cadences, taker strategies (`MomentumStrategy`, `ScalperStrategy`, `NewsHunterStrategy`, `MacroReactionStrategy`), possibly `RunningRound` toxicity weights.
- `server/core`: `MarketMakerStrategy` sensitivity/tolerance defaults if recalibrated (unit tests updated, not weakened).
- Bot economics re-baseline in `docs/calibration.md` (standings will reorder); full suite must stay green.
- No wire-protocol, persistence, or web changes expected.
