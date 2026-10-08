# Design

## Context

See `proposal.md` for motivation and `specs/*/spec.md` for requirements. Today
`FundamentalModel.step()` moves each instrument's latent value `F` by a market factor, a sector
factor, idiosyncratic diffusion, and news jumps (`applyNews`). Trading affects only the order
book and the last traded price `P`; nothing feeds `P`, the book, or net flow back into `F`. The
`InstrumentUniverse` already carries `freeFloat` per instrument, and the runtime already
computes marks and applies fills, so the liquidity scale and the flow signal both already exist.

## Goals / Non-Goals

**Goals:**

- Make trading move the world: net aggressive flow adjusts `F`, transiently and permanently.
- Keep the market tethered: a reversion term and a value anchor prevent unbounded drift.
- Keep determinism: `F` remains a pure function of the seed and the ordered event stream.

**Non-Goals:**

- Imported/historical data (separate change `historical-replay`).
- A full market-impact model (Almgren-Chriss etc.); a tractable, tunable approximation is enough.
- Changing the order book, matching, sessions or accounts.

## Decisions

### D1. Impact is applied to `F`, driven by fills in the runtime

**Decision:** `RunningRound` accumulates net aggressive flow per instrument per tick from the
fills it already applies, then calls a new impact step on the fundamental model.

**Rationale:** The runtime already sees every fill (task 3.4 centralised `applyFill`), so the
flow signal is free and the fundamental change stays in one place. The core stays framework-free.

**Alternatives:** detect flow inside the matching engine (mixes concerns, harder to tune);
derive flow from `BookChanged` events (lossy, more work).

### D2. Two-component impact: transient + permanent

**Decision:** `F` receives `permanentCoef * netFlow` that stays, and `transientCoef * netFlow`
that decays each tick and reverts. A burst moves `F` temporarily; sustained flow accumulates a
permanent re-valuation.

**Rationale:** Matches the two stylized facts users expect (impact decay, permanent impact) and
gives a single dial for each.

**Alternatives:** transient only (no lasting influence — the player can never change the world);
permanent only (every order permanently distorts the market — unrealistic and unbounded).

### D3. Liquidity scaling from free float and depth

**Decision:** Divide the impact by a liquidity measure derived from `freeFloat` (times a base
scale), so illiquid instruments move more per unit flow.

**Rationale:** `freeFloat` is already in the universe; the behavior is the intuitive one.

### D4. Reversion pulls `P` and `F` toward a reference

**Decision:** Keep an explicit per-instrument `reference` (the drift path). A reversion term
pulls `F` toward `reference` and a value anchor (already present as the value bot) pulls `P`
toward `F`. Reversion is weak enough that sustained flow still dominates.

**Rationale:** Prevents unbounded divergence while leaving room for influence; preserves the
existing value-oriented rival as part of the mechanism.

### D5. Parameters are explicit and calibratable

**Decision:** Impact coefficients, reversion strength, and liquidity scale become configurable
fields with defaults; the calibration harness reports the resulting behavior.

**Rationale:** These must be tuned by play-testing; making them observable is what lets us tune.

### D6. Determinism preserved

**Decision:** The flow signal is a deterministic function of the ordered fills; no new
randomness is introduced. The golden-master and replay tests must still pass with flow active.

## Risks / Trade-offs

- **Runaway feedback** (flow → F → bots trade more → F) → Mitigation: reversion term, bounded
  per-tick impact, and a calibration check that `F` stays within a sane band over a long run.
- **Maker always wins / takers always lose** (observed pre-change) → Mitigation: impact makes
  trend-followers profitable during sustained moves and makes the maker's quotes stale; re-run
  the earlier live test and compare rival outcomes.
- **Tuning difficulty** → Mitigation: expose the dials and report them in `docs/calibration.md`.
- **Determinism regression** → Mitigation: the existing replay/golden-master tests gate the change.

## Migration Plan

Additive and self-contained in `core` + `runtime`. Defaults should make the market livelier but
not unstable; if tuning regresses the deterministic tests, the coefficients are the only thing
to adjust. Rollback is resetting the impact coefficients to zero (the feature becomes inert).

## Open Questions

- Exact default impact/reversion coefficients and the liquidity scale — set a first pass, then
  tune via the calibration harness; deferrable, does not change the specs.
- Whether flow is measured over one tick or a short rolling window — a tuning choice; start with
  per-tick and revisit.
