# Design

## Context

See `proposal.md` for motivation. Current state: `tradeFeed` mirrors working
orders, positions (net qty + avg) and account (cash + realized lump) from
`order`/`position`/`account` frames; resync replays the same frames. `Account`
computes realized on reduce/flip but keeps it account-wide; the round journal
(`JdbcEventLog`, per round, readable from any seq) holds every `OrderReport`
with participant ids. No per-player history exists anywhere.

## Goals / Non-Goals

**Goals:** a tab that proves the resume promise — holdings, account, working
orders, history — with every number traceable to server state.

**Non-Goals:** analytics beyond the row math (win rate, drawdown — pro-mode
later); cross-round history (the journal is per round; the purse carries wealth
across); custody billing.

## Decisions

### D1. Per-symbol realized lives in core `Account`

**Decision:** A `Map<String, Double>` beside `realizedPnl`, updated at the same
place the lump sum moves (reduce/flip branches of `applyFill`), exposed on
position state and carried into the purse write-back.

**Rationale:** The lump sum's logic is already correct including flips; splitting
it per symbol at the same site cannot diverge from it, stays deterministic, and
needs no event changes.

**Alternatives:** deriving realized in the history fold (duplicates lot logic in
a second place — drifts apart eventually).

### D2. History folds the journal, not a new table

**Decision:** `POST /api/rounds/fills` reads the round journal and folds
`OrderReport` FILL/PARTIAL_FILL entries per requesting participant into fill
rows (order id, symbol, side, quantity, price, fee, tick). No new table, no
write path — the journal is already the source of truth.

**Rationale:** Zero new durable state to keep consistent; replays and recovery
regenerate identical history for free.

**Alternatives:** a fills table written per execution (faster queries, but a
second source of truth to reconcile on recovery — not worth it at this scale).

### D3. Tab consumes resync + history, replaces the mirror

**Decision:** On mount and on reconnect the tab loads positions/account/orders
from the resync frames and fills from the history query, clearing local state
first (extending the existing `tradeFeed.reset()` pattern).

**Rationale:** Same reconciliation semantics as the terminal already uses; a
returning player cannot see stale-merged rows.

## Risks / Trade-offs

- **Journal scan cost** → a full-round scan per tab open; rounds are tens of
  thousands of events at most — fine, and cacheable per round+participant later.
- **Intraday only** → settlement flattens everything, so history naturally ends
  at the close; cross-round careers read the purse, not this query.
- **Marks are last-trade** → unrealized uses the same marks as everywhere else;
  side-aware bid/ask marks stay a later refinement.

## Migration Plan

Additive only: one core field, one read endpoint, one web component. No
migrations (no new tables), no wire breakage (new frames/endpoint only).

## Open Questions

None that change the approach — interval and column order are presentation
details for implementation.
