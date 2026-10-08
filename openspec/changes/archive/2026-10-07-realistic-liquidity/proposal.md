# Proposal

## Why

Market orders always fill instantly at one price because makers quote flat,
unconditional single-level walls. Real books are thin at the touch, deep
behind it, and makers flinch when flow turns toxic — without that, size never
hurts and the fee preview is the only cost that bites.

## What Changes

- Depth-profile quoting: small at the touch, larger deeper, so orders visibly
  walk the book and slippage prints level by level.
- Stress response: flow-imbalance and realized-volatility signals shrink maker
  size and widen spreads per maker, up to full withdrawal — thin books and
  occasional unfilled market orders included.
- Refresh discipline: repost only changed levels, slower outer levels,
  staggered maker cadences, so the event flood stays bounded.
- Markout stats per fill (fill price vs mid N ticks later) feeding calibration
  and, later, the fills history.

## Capabilities

### New Capabilities

- `realistic-liquidity`: laddered maker quotes, toxicity-driven quote fade and
  withdrawal, disciplined refresh, and per-fill markout measurement.

### Modified Capabilities

None — matching, events and the wire are untouched; quotes remain ordinary
limit orders through the existing submit path.

## Impact

- Core bot strategies (maker quote shape) and the runner refresh loop; no
  matching-engine changes.
- Event volume shifts with ladder depth — journal, checkpoint and broadcast
  load get re-measured, not redesigned.
- Calibration gains depth-shape and stress scenarios; bot-economics baselines
  move (makers trade less into toxicity).
- Docs: liquidity behavior in `docs/calibration.md`.
