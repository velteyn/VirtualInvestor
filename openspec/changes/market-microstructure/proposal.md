# Proposal

## Why

The market's latent fundamental value moves only on its own synthetic random walk; trading
never feeds back into it. A player's or bot's flow can move the traded price around the
fundamental but cannot influence the world, so the market feels like something you watch rather
than something you can push. Real markets are two-way: order flow moves prices and, durably,
fundamentals.

## What Changes

- Introduce **order-flow feedback**: net aggressive flow in an instrument continuously adjusts
  its latent fundamental value, with both a transient (reverting) and a permanent (durable)
  component, so cumulative buying can move the market up and it partially stays there.
- Introduce a **fundamental anchor / pull**: value-oriented participants and a reversion term
  pull the traded price and the fundamental back toward a reference, so the market does not
  drift without bound and mean reversion is observable.
- Make the **magnitude of impact depend on liquidity** (free float / book depth), so large
  orders move illiquid names more than liquid ones.
- Expose the resulting quantities (impact per unit flow, reversion strength, current
  fundamental vs reference) so they can be tuned and observed.

Explicit non-goals for this change:

- No imported/historical data (a separate, later change: `historical-replay`).
- No new instruments, order types, or account mechanics.
- No change to the deterministic contract: the world remains a pure function of seed and event
  log.

## Capabilities

### New Capabilities

- `order-flow-impact`: How trading moves the market — the transient and permanent impact of net
  aggressive flow on the latent fundamental value, its scaling by liquidity, and the reversion
  that tethers price to a reference.

### Modified Capabilities

- `market-simulation`: The "Hybrid fundamental value" requirement gains a flow-driven impact
  term, and "Order-driven price discovery" is strengthened so that price deviation from the
  fundamental feeds back into the fundamental rather than only sitting beside it.

## Impact

- **Core**: `FundamentalModel` (and a new impact/reversion component) gain a flow term; the
  instrument universe already carries `freeFloat`, used as the liquidity scale.
- **Runtime**: `RunningRound` accumulates net aggressive flow per instrument per tick (from
  fills) and applies impact; market rules and sessions are unchanged.
- **Tuning/observability**: impact and reversion parameters become explicit and calibratable
  (`docs/calibration.md`), and the calibration harness reports the new behavior.
- **Determinism**: unchanged in principle; the flow term is a deterministic function of the
  event stream, and the golden-master/replay tests must still hold.
