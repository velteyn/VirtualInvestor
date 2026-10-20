# Design

## Context

See `proposal.md` for motivation. Current state: `NewsGenerator` builds earnings + random + `ScenarioDeck` arcs from the `news.random` stream; `NewsEngine` stages rumor → confirm/deny via `PlannedNews`; strategies read `BotContext.news` (`NewsSignal`: signed magnitude, validity window); `TemplateNarrator` builds bodies from headline + subjects. Bots have fixed `orderQuantity`, no personality. `ScenarioDeck.Beat` already proves the `{name}`/`{sector}` slot pattern and bible mechanism.

## Goals / Non-Goals

- Goals: per-bot dispersion scaling, source reliability tiers with tiered confirm rates, authored rumor/confirm/deny triples per story, scope template packs shared by wire and template narrator.
- Non-Goals: `Dbrelat` contagion, corrupt-analyst bias, gatekept prestige content, TakeoverBid settlement, cast renaming/lore, sidecar prompt redesign.

## Decisions

1. **Dispersion lives on the strategy, applied at read time.** Add a `dispersion` double (0–50, default per archetype: hunter 5, macro 10, value/momentum 15, scalper 25) to `NewsHunterStrategy`, `MacroReactionStrategy`, and any headline-reading strategy; perceived magnitude = `signal.signedMagnitude() * (1 − 0.01 × dispersion)`. Alternative (central scaling in the engine when building `BotContext`) rejected: `BotContext` is built per decision and shared shape; per-strategy scaling keeps the engine untouched and makes calibration per-archetype.
2. **Reliability tier is a field on `NewsItem` and `NewsSignal`, drawn from a dedicated `news.tiers` stream.** New enum `ReliabilityTier { WIRE, PATRON, SPY }` in core.news; generator rolls tier per item (wire 60 / patron 30 / spy 10) off its own stream so the event stream's draws never shift, and the confirmed-draw for routine items uses tiered bias (wire 0.8, patron 0.6, spy 0.35) instead of the uniform 50/50 legacy mapping. Scenario finales keep the deck's `confirmBias` (see 4). Strategies each declare trusted tiers (hunter: all; macro: wire only). Alternative (derive tier from `NewsSource`) rejected: sources are outlets, tiers are trust; conflating them breaks the existing 3-outlet fiction.
3. **Triples ride on the story, resolution headlines selected by the engine.** Add `confirmHeadline` + `denyHeadline` to `NewsItem` (nullable for unstaged earnings); `NewsEngine` emits the rumor lede at release and the confirm/deny lede at resolution. Template packs author all three at generation time. Alternative (triples only in narrator) rejected: the wire headline itself must change at resolution per the spec — the narrator never rewrites wire headlines.
4. **Scenario finales are decoupled from tier multipliers.** The deck's `confirmBias` alone decides a finale: a major shock's fate is market structure, not a function of the carrying tier. Tiers govern trust reads (who acts) and routine-item confirms (the legacy uniform staging keeps the tier-biased draw). Rationale: multiplying violent ends by 0.35–0.8 made breaker halts ~5–10x rarer in small seed windows, leaving the LULD system untested; halts stay rare-but-present at deck rates. Rejected alternative (tier × beat): realistic halt frequency beats uniform treatment of noise and shocks.
5. **One `StoryTemplates` class owns all packs, shared by generator and narrator.** `core.news.StoryTemplates`: per-scope lists of `(rumor, confirm, deny, body)` tuples with `{name}`/`{sector}` slots, ≤200 chars each, day-window gating metadata for the taunt pack. Generator picks entries with `news.random` draws (replay-safe); `TemplateNarrator` imports the same packs for fallback bodies. Alternative (duplicate packs in narrator module) rejected: wire and template prose must never disagree.
6. **Day-windowing from tick fractions, no clock.** Taunt eligibility = `tick ≥ startTick + sessionTicks × gateFraction`, same pattern as `scenarioBase`. No new time source, no persistence.

## Risks / Trade-offs

- [Risk] `NewsItem` record gains 3 fields; every constructor call site (generator + tests) updates → Mitigation: single PR, compiler enforces exhaustiveness; no wire-protocol break (edge maps to existing DTO + new optional headline fields).
- [Risk] Tiered confirm bias shifts existing calibration baselines (hunters lifetime-negative) → Mitigation: recalibrate per `docs/calibration.md`; tiers default wire-heavy so drift is small.
- [Risk] Authored triples multiply headline content ~3× → Mitigation: packs are data tables with a `FictionalNames` unit test, same as `ScenarioDeck` today.
- [Risk] Dispersion makes noisy bots skip valid headlines → Mitigation: intended behavior (crowd-splitting); thresholds documented per archetype.

## Migration Plan

- No persistence migration: triples and tiers are generated per round, never stored except inside already-persisted article headline/body text.
- Rollback: revert generators + strategies; old seeds still replay because new draws consume a documented suffix of the `news.random` stream (draw order: tier, triple index, then legacy draws unchanged).

## Open Questions

- None blocking. Per-archetype dispersion defaults and tier confirm rates get final numbers in calibration during implementation.
