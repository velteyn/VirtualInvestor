# Design

## Context

See `proposal.md` for motivation. Current state: `terminal.ts` `onOrder` sends a
fixed 100-share order straight through `ServerClient.placeOrder`; depth frames
already stream best bid/ask; bank terms are fetchable via `/api/players/banks`;
`OrderReport` carries rejection reasons that never reach the wire (`OrderDto`
has no `reason`).

## Goals / Non-Goals

**Goals:** one transmit path with sizing, preview and reasons; chart gestures
become prefill.

**Non-Goals:** brackets/TP-SL (needs linked-order wire + engine work — separate
change); one-click mode (always-preview while players learn); ticket-as-modify
(`modifyOrder` is price-only; qty changes stay cancel+replace for now).

## Decisions

### D1. Ticket lives under news in the right column

**Decision:** New component in the column stack, full-width blue/red side toggle
on top; on phones it stacks with everything else, no special layout.

**Rationale:** Entry forms are narrow and vertical; the column is dedicated
while the bottom dock stays for state views. Matches TradingView's panel
placement.

### D2. `onOrder` prefills, never transmits

**Decision:** Chart menu and Buy/Sell buttons populate the ticket (side, type,
chart price) and stop; drag-to-modify keeps calling `modifyOrder` directly.

**Rationale:** Single transmit path means one place for validation, preview and
reasons; chart-speed entry survives as prefill.

### D3. Touch-prefill follows until edited

**Decision:** A `dirty` flag per price field: clean fields track best
bid (sells) / ask (buys) from depth frames; any keystroke pins the value.

**Rationale:** Most limits want touch ± a tick; typing full prices, especially
on phones, is the slowest part of the flow.

### D4. Fee preview from bank terms, guard from cash

**Decision:** Preview computes `qty x price + max(ticket minimum, per-share x
qty)` from the player's bank (fetched once per session) against `tradeFeed`
cash; Transmit disables over buying power with the reason inline.

**Rationale:** Mirrors the server formula exactly, so the preview never lies;
the ticket teaches costs, which is the game's point.

### D5. Rejection reason rides the order frame

**Decision:** Add `reason` to `OrderDto`, populated from `OrderReport.reason()`
at the existing broadcaster mapping; the ticket shows the latest reason for the
offending order id and keeps all fields.

**Rationale:** Smallest wire delta that closes the loop; no core or matching
changes.

## Risks / Trade-offs

- **Stale touch on illiquid names** → prefill from the last depth frame could be
  old; mitigate by greying the preview when the mark is older than N seconds
  (mark frame times are on the payloads).
- **Prompt-fatigue** → always-preview adds a step per order; accepted for v1
  while players learn, one-click later.
- **Phone keyboards** → numeric inputs with `inputmode="decimal"`; keep tab
  order side → type → qty → price → transmit.

## Migration Plan

Additive web component + one additive wire field. Rollback is the previous
bundle; `onOrder` revert is a five-line change. No migrations, no core changes.

## Open Questions

None that change the approach.
