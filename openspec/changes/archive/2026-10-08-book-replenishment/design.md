# Design

## Context

See proposal.md (Why). Current mechanics, all verified in code:

- `BotRunner.refreshQuote` requotes rung `i` when `(tick + phase) % (50 * (i+1)^2) == 0`: touch every 50 ticks, rung 2 every 200, rung 3 every 450. In a 180-tick session only the touch ever requotes; consumed outer depth is gone for the game.
- `QuoteTrafficTest` enforces the <2x event-traffic budget — any faster cadence must keep it green.
- Takers (`Momentum`, `Scalper`, `NewsHunter`, `MacroReaction` via `BotRunner.take`) fire market orders without reading the book; ~1,412 reject vs ~1,530 trades on seed 42.
- Toxicity `FlowToxicity(1000.0, 1.0, 0.97)`; makers MARK 1.0/4.0, ATLAS 3.5/2.75, CROWN 4.5/2.25 (sensitivity/tolerance). Baseline mean toxicity 0.23–0.58 benches CROWN/ATLAS on most symbols.
- Hard constraint: everything stays deterministic (seed + tick functions only, no new randomness) so replays and `OrderFlowDeterminismTest` hold.

## Goals / Non-Goals

**Goals:**

- Every rung requotes several times per session at any configured session length, traffic budget intact.
- Blind takes disappear: aggressive orders size to visible depth.
- Baseline flow keeps ≥2 makers quoting; genuine stress still thins the book; gate trips stay zero.

**Non-Goals:**

- No wire-protocol, persistence-schema, or web changes.
- No new bot personalities or strategy rewrites — clamping, not new brains.
- No change to the tolerance backstop's role (safety net, per the reworded spec).

## Decisions

**1. Session-relative tier cadence (over fixed faster tiers).**

Derive each rung's period from the session length: rung `i` refreshes every `sessionTicks / K(i)` ticks with quadratic ratios preserved (touch fastest), so every rung requotes ≥3× per session at any duration. Fixed faster tiers (e.g. touch 10 / outer 40) would over-quote short sessions and under-quote long ones; session-relative scales both ways. `QuoteTrafficTest` stays the objective gate — if the budget breaks, cadences slow, not the test that weakens.

**2. Clamp takes at the `BotRunner.take` choke point (over per-strategy checks).**

`take` already receives the engine: read touch depth and spread, size the market order to a fraction of visible depth, skip (leave the signal for the next tick) when the touch is empty or the spread exceeds a multiple of the maker's base. One deterministic clamp covers all four taker strategies identically; per-strategy edits would quadruple the work and drift apart. Strategies keep producing *desired* trades; the runner reconciles desire with reality — the same split makers already use (quote vs resting depth).

**3. Move one calibration knob: the flow weight (over retuning maker personalities).**

Lowering `TOXICITY_FLOW_WEIGHT` shifts every symbol's baseline down uniformly and preserves the MARK < ATLAS < CROWN nervousness ordering that makes books thin unevenly. Retuning six sensitivity/tolerance numbers instead would re-personality the makers with no extra benefit. Exact value comes from the calibration task (measure baseline means, pick weight so ≥2 makers quote on most symbols); economics re-baseline in `docs/calibration.md` follows, since standings will reorder.

**4. Harden the watch, don't just print.**

`MarketRealismTest` telemetry becomes assertions with measured bounds: reject fraction ceiling, empty-side fraction ceiling per focus symbol, minimum makers-quoting on calm symbols, gate trips == 0. Bounds are calibrated from the fixed system's own output plus headroom — the watch fails on drift, not on noise (seed-pinned, so no flakiness).

## Risks / Trade-offs

- [Faster requotes widen the touch less often → tighter books → more bot flow → new economics] → Mitigation: re-baseline economics in the same change; standings reordering is expected, documented, not a failure.
- [Depth-clamped takers trade less → quieter sessions, fewer fees] → Mitigation: size to a *fraction* of depth rather than all-or-nothing, so flow continues at book-appropriate size; watch asserts trade floor.
- [Session-relative cadence surprises tests that assume 50-tick waves] → Mitigation: run the full suite; update timing-sensitive tests to derive from the same helper, not hardcoded ticks.
- [Recalibration overshoots and the gate starts tripping] → Mitigation: gate-trips assertion in the watch catches it immediately; tolerance values themselves are untouched.

## Migration Plan

Parameters and runner logic only — no data migration, no config format change. Rollback is a revert of the constants plus the runner clamp. Ship order inside the change: cadence → clamp → calibration → watch bounds → economics docs. Each step keeps the suite green on its own.
