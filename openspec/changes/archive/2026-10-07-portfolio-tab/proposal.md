# Proposal

## Why

A returning player currently sees positions and a cash line, but nothing of what
happened while away — completed fills, closed positions and per-symbol gains are
invisible once the stream moves on. For the resume-without-loss promise to hold,
the terminal needs a portfolio tab backed by server state, not just the live
mirror.

## What Changes

- A portfolio tab in the terminal: account header (equity, cash, buying power,
  unrealized + realized + fees), holdings table (`Symbol | Qty | Avg | Last |
  Value | Invested | Unreal $ | Unreal % | Close`), working orders with
  modify/cancel, and a History section of the player's fills and closed trades.
- Per-symbol realized P&L tracked in `Account` (deterministic, codec-safe),
  surfaced on position frames.
- A per-player fill-history query over the round journal (`OrderReport`s folded
  per participant), exposed as a versioned endpoint the tab loads on mount and
  after reconnect.
- The tab reconciles through the existing resync: returning players see current
  positions, what filled while away, and closed trades — nothing is lost by
  closing the browser.

## Capabilities

### New Capabilities

- `portfolio`: holdings/P&L/history tab content, per-symbol realized tracking,
  and the per-player fill-history query behind it.

### Modified Capabilities

None — live frames, resync and purse behavior are unchanged; the tab consumes
them plus the new query.

## Impact

- Core `Account`: per-symbol realized map (additive field, no event changes).
- Edge: one read endpoint over the round journal; wire gains fill-history payloads.
- Web: new tab component fed by `tradeFeed` state plus the history query.
- Docs: portfolio tab run-through in `docs/terminal.md`.
