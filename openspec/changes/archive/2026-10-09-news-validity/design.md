# Design

## Context

See proposal.md (Why). Current mechanics, all verified in code:

- `NewsReleased` carries `magnitude`, `durationTicks` (emitted, never consumed mechanically), scope, symbol, and sector; `NewsEngine` stages rumor → confirm/deny with the fundamental fold absorbing every piece permanently.
- `BotRunner` holds headline signals in a per-symbol mailbox visible at arrival + per-archetype delay (VULTURE 0, MERIDIAN 4 ticks), consumed one-shot; magnitude is jittered per bot per headline from the seed.
- Value, momentum, and maker responses read prices, volatility, and toxicity — never signals.
- Hard constraint: determinism (same seed, same stream) holds throughout.

## Goals / Non-Goals

**Goals:**

- Heterogeneous attention spans: fast-burn and slow-burn headlines coexist, with slow money systematically missing fast news.
- Expiry as a pure function of timeline + tick — no new state, no new randomness, replays agree.
- The newsroom can label expired items "priced in" from data it already receives.

**Non-Goals:**

- No strategy-logic changes (thresholds, sizes, delays untouched).
- No NewsEngine, wire-protocol, or persistence changes.
- No fading relevance curves: hard cutoffs only (legible, testable); decay lives where it already does (volatility).

## Decisions

**1. Reuse `durationTicks` as the validity window (over a new field).**

The field rides every news item today and nothing consumes it mechanically — repurposing disturbs zero behavior. A signal is visible while `tick <= releaseTick + durationTicks`. Alternative (new `validityTicks`) would duplicate a dormant field for no benefit.

**2. Check expiry at visibility time, composed with delay (over filtering at fold time).**

The mailbox check becomes `tick >= arrival + delay AND tick <= release + validity` — one clause, no new machinery, no new state. Fold-time filtering would bake the decision too early and complicate the deterministic fold. Slow money missing fast news falls out with no further code.

**3. Expiry gates headline reactions only, never price-based behavior (over strict blinding).**

Attributing price moves to expired causes would need counterfactual machinery and would cannibalize the momentum/value bots' reason to exist. Value bots read mispricing, not newspapers; makers feel toxicity, not headlines. Locked by user decision in exploration.

**4. Generator rule `validity ∝ magnitude × scopeWeight`, confirmed against the watch (over per-event authoring).**

No hand-tuning 40 headlines a session: earnings (company, 1–4%) land in the hundreds of ticks, random noise shorter, Hormuz-class sector/macro shocks in the thousands. Exact constant from the watch's cascade shape; the economics re-baseline absorbs the shift.

## Risks / Trade-offs

- [Long-validity shocks pin toxicity/volatility for days → makers fade whole regimes] → Mitigation: intended and spec-aligned; gate-trips assertion bounds the extreme.
- [Delay > validity silences MERIDIAN on whole news classes] → Mitigation: correct per the locked semantics (slow money misses fast news); the watch's desk-activity check will show it explicitly rather than hiding it.
- [durationTicks repurposing confuses future readers] → Mitigation: document at the field (code comment) and in `docs/news.md`; the name already says what it now means.

## Migration Plan

Parameters plus one clause — no data migration, no config format change. Rollback is a revert of the check (mailbox behaves as today). Ship order: visibility check + generator rule with unit tests → watch run, cascade and bound confirmation → economics docs. Each step keeps the suite green on its own.
