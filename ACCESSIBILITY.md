# Accessibility

VirtualInvestor is a real-time trading game. We want everyone who wants to
play to be able to play — and we are upfront about where we fall short.

## What works today

- All controls are native buttons, links, and form fields (ticket, lobby,
  dialogs), so they are focusable and operable without a pointer.
- Pages are structured with headings and landmarks; error messages use
  `role="alert"` and are announced as they appear.
- Nothing essential is conveyed by motion alone.

## Known barriers

- **The chart is a canvas.** Prices, books, and overlays render on a
  `<canvas>` that screen readers cannot navigate. The same data is
  partially available as text — watchlist last prints, positions with
  entry prices and P&L, the portfolio tables, and the news wire — but
  chart shapes (candles, depth, overlays) have no text equivalent yet.
- **The clock never pauses.** Sessions run in real time with auction
  windows measured in seconds, which penalizes anyone who needs more
  time to read and react. There is no turn-based or slowed mode.
- **Gains and losses are color-coded** (green/red) with `+`/`-` signs;
  the signs carry the meaning, but we have not audited contrast or
  color-vision safety.
- We have never done a full keyboard-only walkthrough or tested with a
  screen reader end to end.

## Roadmap

Text equivalents for chart state, a reviewable event log that does not
depend on split-second timing, and a contrast pass are the three items
that would move the needle most. Contributions in this area are
especially welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## Reporting a barrier

If something in the game blocks you, tell us: open an issue labeled
`accessibility` describing what you tried, what you expected, and what
assistive technology you use — or email
[ethanol-basin-yam@duck.com](mailto:ethanol-basin-yam@duck.com) if you
prefer not to post publicly.
