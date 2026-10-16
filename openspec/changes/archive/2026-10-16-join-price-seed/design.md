# Design — Join price seed

## Context

See `proposal.md`. `GET /api/rounds/history` (via `client.getHistory`) serves
in-memory warm-up bars in milliseconds; the chart already relies on it. The
ticket and panels each keep private mark maps fed solely by the socket.

## Decisions

### D1 — Seed-only-if-absent, per owner map

Ticket seeds `marks`/`touch` for the selected underlying; panels seed
`prices` for watch symbols. Guarded by `!map.has(symbol)` so live data always
wins and re-seeds never clobber. Failures (`catch`) keep today's behavior.

### D2 — Touch seeded as last close on both sides

History carries no book, so bid = ask = close. This makes touch-prefill and
the fat-finger guard functional immediately; the first depth frame replaces
it. Marked in code as a seed, not a quote.

### D3 — No shared helper

Two call sites with different maps and follow-ups (ticket: followTouch +
preview + ladder; panels: watchlist render). A shared helper would couple
them for ~6 lines saved — duplicated 6-liners with a comment instead.

## Risks

- History missing for a symbol (unknown) → caught, dark as today.
