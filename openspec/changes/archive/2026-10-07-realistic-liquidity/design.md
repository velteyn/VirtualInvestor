# Design

## Context

See `proposal.md` for motivation. Current state: `MarketMakerStrategy.quote`
returns one bid + one ask at fixed size; `BotRunner` cancels and reposts both
every `QUOTE_REFRESH_TICKS`, unconditionally and synchronized across makers.
The half-spread already widens with volatility, but quotes never shrink or
pull. The 681-orders-in-15s stream measured during flood debugging is the
budget every refresh decision spends from.

## Goals / Non-Goals

**Goals:** visible walk-the-book slippage, stress-reactive makers up to full
withdrawal, flat-or-better event traffic, measured markouts.

**Non-Goals:** matching-engine changes (price-time priority already does the
walking); new order types (icebergs, pegs); quote obligations or penalties.

## Decisions

### D1. Laddered `Quote`, diffed repost

**Decision:** `Quote` gains per-level sizes (prices stay touch-stepped multiples
of the half-spread); `refreshQuote` diffs desired levels against resting ids
and cancels/reposts only changed ones, keyed by level index.

**Rationale:** The matching engine needs no changes — depth shape emerges from
ordinary limit orders. Diffing is what keeps a 5-deep ladder near today's
event rate instead of quintupling it.

**Alternatives:** full cancel/replace per refresh (simple, multiplies the
flood — rejected by measurement).

### D2. Toxicity signal from flow imbalance and realized vol

**Decision:** Per-symbol stress = weighted sum of signed-flow imbalance over a
short window plus realized volatility; each maker gets its own sensitivity and
tolerance drawn at roster build, so MARK may hold while VULTURE pulls. Size
scales down first, spread second, zero last.

**Rationale:** Heterogeneous flinching is what makes books textured instead of
gappy-everywhere; determinism holds (signal derives from the event stream).

**Alternatives:** single global stress switch (all makers vanish together —
flash-crash generator, and boring).

### D3. Staggered, tiered cadences

**Decision:** Touch refreshes fastest, outer levels slower; each maker's phase
offsets by roster index. A level whose recomputed price/size matches resting
within epsilon is left alone.

**Rationale:** Staleness between refreshes is itself realism (quote lag causes
micro-slippage); staggering breaks the lockstep that makes all books move as
one.

### D4. Markouts ride the journal, not the hot path

**Decision:** Record fill id, price and tick; a fold over the journal joins the
mid at tick+N later. No per-tick computation, no new events.

**Rationale:** Measurement must never cost the simulation; the journal already
holds everything the fold needs.

## Risks / Trade-offs

- **Unfilled market orders** → withdrawal means genuine "no liquidity"
  rejects for humans; mitigation: tolerance floors keep majors quotable, and
  the ticket already surfaces rejection reasons.
- **Calibration drift** → bot economics shift (makers trade less into
  toxicity); mitigation: re-baseline LEDGER/MARK economics in calibration,
  same as the fee change did.
- **Journal growth** → more fills from walking orders; bounded by the same
  pruning that fixed the checkpoint stalls.

## Migration Plan

Strategy + runner changes only, behind existing seams; solo-game baselines
re-recorded. Rollback is reverting two classes. No migrations, no wire
changes (markouts read the journal, not new frames).

## Open Questions

- Ladder depth and size profile per name (start 5 levels geometric; confirm in
  calibration).
- Stress window length in ticks (start 30; confirm in calibration).
