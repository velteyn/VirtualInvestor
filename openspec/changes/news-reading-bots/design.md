# Design

## Context

See proposal.md (Why). Current mechanics, all verified in code:

- `NewsEngine.advance` runs at step (4) and returns `NewsReleased` events carrying scope, symbol, sector, headline, and magnitude — no engine changes needed.
- Strategy runners act per engine per tick at step (5); `BotRunner.take` builds `BotContext` with the news signal hardcoded to `null` — the only production construction site (tests pass signals explicitly).
- `NewsHunterStrategy` (VULTURE, 40 shares) fires on any signal above its 0.01 threshold with no scope check; `MacroReactionStrategy` (MERIDIAN, 1,000 shares) fires on MACRO scope only. All other strategies ignore the signal.
- Hard constraint: determinism (same seed, same stream) and the <2x traffic budget hold.

## Goals / Non-Goals

**Goals:**

- Headline strategies fire on the release tick through the existing, tested strategy code.
- One headline can never stampede every engine: bounded, reviewable order flow per release.
- No new informational asymmetry for humans beyond today's (bots already trade the fundamental move the same tick).

**Non-Goals:**

- No strategy-logic changes (thresholds, sizes, scope checks stay exactly as written).
- No NewsEngine, wire-protocol, persistence, or web changes.
- No rumor-vs-confirmation trading distinction: the signal is the headline; resolution plays out through fundamentals and volatility as today (denials leave rumor-chasers holding the bag — realistic).

## Decisions

**1. Fold releases into signals in `RunningRound.step`, between news (4) and bots (5) (over enriching `NewsEngine`).**

The release events already carry everything (`scope`, `symbol`, `sector`, signed `magnitude`); the world collects them into a per-symbol signal map for the tick and passes it down. The engine stays a pure event source. Alternative (engine returns signals directly) would tangle presentation with simulation for no benefit.

**2. Route by scope: company → its symbol, sector → its symbols, macro → the index only (over broadcasting to all engines).**

Runners act per engine, so naive delivery fires VULTURE on all 21 symbols per macro headline (~840 scattered shares) and MERIDIAN at 1,000 shares × 21 engines — a flash flood no spec wants. Index-only macro routing bounds a macro event to one hunter take plus one institutional take, matching both strategies' declared sizes. Company and sector routing follow the release's own addressing.

**3. Signal the headline (release), not the resolution (over confirm/deny signals).**

What a reader — human or bot — reacts to is the headline. Confirmations and denials already move prices through the fundamental fold; sending a second signal would double-count resolutions and hand bots a risk-free rumor fade. Hunters buying denied rumors eat the loss like everyone else.

**4. Same-tick delivery is fair (over delaying bots a tick for humans).**

Bots already trade the fundamental repricing on the release tick through value/momentum paths, and makers widen the same tick — the informational playing field does not move. A delay would invent an advantage humans never had while complicating the deterministic fold.

## Risks / Trade-offs

- [Two bots wake up → flow, toxicity, fees, and standings all move] → Mitigation: re-baseline economics in the same change; the market watch (with teeth) quantifies the shift instead of eyeballing it.
- [News-day toxicity spikes → makers fade harder on headlines] → Mitigation: intended and spec-aligned (fade exists for toxic flow); gate-trips assertion guards the backstop.
- [Sector news fans out to several engines (~7 × 40 shares)] → Mitigation: bounded and visible in the watch's trade counts; no cap needed unless measured otherwise.
- [A release and its resolution in nearby ticks double-moves prices] → Mitigation: pre-existing dynamics (fundamental fold unchanged); watch max-toxicity bounds the combined effect.

## Migration Plan

Wiring only — no data migration, no config format change. Rollback is a revert to the `null` signal. Ship order: signal fold + routing with unit tests → watch run and bound recalibration → economics docs. Each step keeps the suite green on its own.
