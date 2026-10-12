# Future ideas

> Inspiration reference: `docs/wst2001-inspiration.md` (Wall $treet Trader 2001
> reconstruction: futures, options, bank-loan margin, crew) — active arc starts
> at change `futures-options`.

A ranked list of ways to make VirtualInvestor cooler, based on an analysis of what the
architecture already makes cheap. Each idea notes the lever it pulls and the rough effort.
Nothing here is scheduled; when one is picked up it should go through an OpenSpec change
like everything else.

## Where the project stands

**Strengths worth building on:**

- **Determinism** — same seed, same market, proven by the calibration harness
  (`docs/calibration.md`). This is the project's superpower.
- **Durable event log** — every session is reproducible and resumable
  (`docs/persistence.md`).
- **Real microstructure** — laddered books, auctions, permanent+transient impact,
  toxicity-aware market makers, news-latency bots (`docs/liquidity.md`, `docs/bots.md`).
- **An LLM sidecar** (narrator) that currently only writes headline prose
  (`docs/news.md`).

**Thin spots:**

- Zero meta-progression: every session is one-off; accounts gate access but remember
  nothing about how you trade.
- One asset class: equities only, no leverage/convexity dimension.
- The narrator sidecar idles after writing headline bodies.
- Sessions end with a whimper — settlement, no post-game moment.
- No replay, no spectating — though the architecture practically begs for both.

---

## Tier 1 — the architecture already paid for these

### 1. Film Room (session replay)

The event log plus the deterministic engine make replay almost free. After a round
settles, re-run the journal and render it in the terminal: a scrubber, the player's fills
annotated on the chart, news markers on the timeline. Players will love reviewing the
exact moment they got margin-called. Highest value-per-effort feature available.

### 2. Daily Challenge (shared seed)

Determinism means everyone who plays the same seed gets the identical market. Publish a
daily seed with a fixed config and keep a global leaderboard for it. Instant competitive
stickiness with zero new simulation code — a lobby feature plus a results table.

### 3. Time-Travel "What-If" sandbox

Because a round is a pure function of (seed, events, orders), a settled session can be
branched: rewind to tick N, place hypothetical orders, re-simulate forward. "What if I'd
bought the rumor dip instead of panic-selling?" A killer educational and drama feature
almost no trading game can offer.

### 4. Spectator mode

Let anyone open a read-only terminal on a running room from the lobby. Friends watching a
LAN final is a party moment. It is the market-streaming spec minus order intake.

## Tier 2 — make the LLM sidecar earn its VRAM

### 5. Talking rivals

VULTURE, MARK, BLITZ and friends already have strong archetypes (`docs/bots.md`). Have
the narrator give them voices: a one-line quip on a big trade, a gloat when they pass the
player on the leaderboard, panic during a halt. Personality turns bots from spreadsheet
rows into opponents.

### 6. AI Market Wrap

At settlement, generate a post-game newspaper article: "TAPE EVENING EDITION — NVDO
slides 4% on denied rumor; Ana out-foxes VULTURE at the close." Feed the model the
journal (final standings, biggest trades, news timeline). Cheap prompt, huge payoff — a
shareable artifact per session.

### 7. AI Coach

Per-symbol realized gains and fill markouts are already computed. At game end, have the
LLM review the player's worst-markout fills and explain the mistake in plain language.
The feature that turns a game into a teacher.

## Tier 3 — meta-game (stickiness)

### 8. Career profile and ratings

Persistent per-player stats across sessions: an ELO-style rating against rivals and
humans, win rate, average return, max drawdown survived, favourite ticker. The schema is
already account-based; this is aggregation.

### 9. Achievements

"Survived a circuit-breaker halt", "Beat VULTURE on a heavy-news day", "Front-ran ATLAS
BANK's VWAP". These are event-log queries over data already stored.

### 10. Tournaments and seasons

Scheduled multi-room events with brackets, or a weekly ladder reset. Builds on the career
profile.

## Tier 4 — gameplay depth (bigger lifts)

### 11. Player algo orders

Let the player submit VWAP / TWAP / iceberg orders instead of only manual slicing.
`VwapExecution` already exists in `core/bot` — generalize it for human participants.
High realism payoff, moderate effort.

### 12. Options

Delivered by change `futures-options`: intraday cash-settled futures (one per
underlying, 20% default margin) plus European calls and puts (ATM ± 3 strikes,
one expiry at the closing bell, deterministic formula premia). Deterministic
Black-Scholes off the existing fundamental model; everything expires at the
closing bell. Multi-expiry chains are an explicit follow-up.

### 13. Scenario pack / story mode

Handcrafted seeds curated as named scenarios: "Short Squeeze Tuesday", "The Denied
Rumor", "Macro Shock Monday". Essentially the Daily Challenge with editorial curation and
a difficulty ladder — a campaign mode.

## Quick wins (days, not weeks)

- **Sound design** — fill blip, news ding, margin-call klaxon, closing bell. Small code
  footprint, transforms the feel.
- **Depth-of-market ladder** next to the ticket — full book data is already on the
  socket.
- **Time & sales tape** — a scrolling print feed; trade frames already stream.
- **Circuit-breaker cinema** — full-screen halt banner with a countdown (halts already
  exist).
- **Rival activity feed** — even without the LLM: "VULTURE bought 2,000 NVDO" ticks into
  the news panel, optionally with the archetype's motive ("TIDE is chasing momentum in
  SOLR").

---

## Recommended order

1. **Film Room replay** — nearly free on this architecture, and the feature nobody else
   has.
2. **AI Market Wrap + talking rivals** — the sidecar weights are already downloaded;
   mostly prompt work.
3. **Daily Challenge** — determinism turned into competition, tiny scope.
4. Then choose between **player algo orders** (realism) and **options** (depth),
   depending on whether the goal is deeper trading mechanics or broader gameplay.
