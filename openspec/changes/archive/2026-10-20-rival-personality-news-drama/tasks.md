# Tasks

## 1. Personality core (dispersion + tiers)

- [x] 1.1 Add `ReliabilityTier` enum and `tier` field to `NewsItem`/`NewsSignal`/`NewsReleased`, generator rolling tier from a dedicated `news.tiers` stream (wire 60/patron 30/spy 10, event stream untouched), and verify `mvn -pl core test` passes on new tier-distribution test
- [x] 1.2 Add `dispersion` to headline-reading strategies (hunter 5, macro 10; value/momentum/scalper trade price action so dispersion is N/A — documented in 1.4) scaling perceived magnitude as `(1 − 0.01 × dispersion)`, and verify borderline-headline crowd-split unit test passes
- [x] 1.3 Apply tiered confirm bias (wire 0.8 / patron 0.6 / spy 0.35) to the legacy uniform staging and verify same-seed replay test passes. Scenario finales keep the deck's own bias (decoupled: tiers govern trust and noise, not major-shock fate) so halt-inducing ends stay rare but present.
- [x] 1.4 Document defaults and trust matrix in `docs/news.md` + `docs/bots.md`, and verify docs match strategy constructor defaults (`NewsHunterStrategy` 5, `MacroReactionStrategy` 10; price-action archetypes take no dispersion)

## 2. Staged triples + template packs

- [x] 2.1 Add `StoryTemplates` pack tables (takeover, regulatory, guru, analyst-color, taunt) with rumor/confirm/deny triples, `{name}`/`{sector}` slots, ≤200 chars, `FictionalNames`-clean, day-gated taunts, and verify pack unit test (slots fill, lengths, fiction guard) passes
- [x] 2.2 Wire generator to author triples per story (earnings keep single headline + pack body; random/scenario items draw pack entries from `news.random` — content selection on the separate `news.packs` stream) and add `confirmHeadline`/`denyHeadline` to `NewsItem`, and verify generator test shows distinct stage ledes
- [x] 2.3 Make `NewsEngine` emit the stage's lede at resolution (confirm-lede or deny-lede referencing the rumor; `NewsConfirmed`/`NewsDenied` carry the headline, rumor headline as fallback), and verify engine test asserts resolution headline differs from rumor headline
- [x] 2.4 Make `TemplateNarrator` render fallback bodies from the same packs by scope+stage, and verify narrator test shows template body agreeing with wire lede subjects (sector test + determinism test; taunts excluded from the newsroom in `kindOf`)

## 3. Integration

- [x] 3.1 Run full suite (`cd server && mvn test`, `cd web && npm test -- --run`, `npm run build`) and verify green, then update `docs/calibration.md` baselines for hunter/macro lifetime P&L and verify baselines documented. Pre-existing: `RoundRecoveryServiceTest` (2 tests) fails identically on the clean tree — unrelated to this change, left red.
