# Wall $treet Trader 2001 — inspiration notes

Research reference for the derivatives/leverage arc. Written Oct 2026 from
secondary sources because **no official WST 2001 manual PDF is publicly
archived** (checked archive.org, oldgamesdownload, MobyGames, PC Gamer scans,
GameFAQs). Nothing below quotes the manual; it reconstructs mechanics from the
MYTH NFO files, MobyGames entries, the GameSpot review of WST 2000, and
retro-replay/vgtimes summaries.

## The game

- Monte Cristo, Europe 2000. You are a business graduate backed by Juliette
  Fleming's venture capital (Lord Fleming died in Guatemala — the 2001 intro).
- 7 industry scenarios (music, cars, luxury goods, ...), real company names,
  multiple currencies, ~16 sectors by late game (2000 figure).
- Real-time with a speed slider; scenarios run 3–6 month windows (per GameSpot).
- Multiplayer over network/Internet existed in 2000, but reportedly empty.

## Core loop

```
  [Venture capital] --> [7 scenarios, pick sector]
                              |
                              v
              +--------------------------------+
              | REAL-TIME TAPE (speed slider)  |
              +--------------------------------+
                |           |           |
                v           v           v
          +----------+ +---------+ +---------+
          | News     | | Pub     | | Team    |
          | Centre   | | rumors  | | hire    |
          +----------+ +---------+ +---------+
                |           |           |
                +-----+-----+-----------+
                      v
          +--------------------------+
          | Trade: Shares, Futures,  |
          | Call + Put Options       |
          +--------------------------+
```

## Amenity briefs

### 1. Options — confirmed

Both NFOs list verbatim: "Shares, Futures and Call and Put Options". No strike /
expiry / exercise detail survives in any source found. GameSpot confirms T-Bills
and rate-sensitive pricing existed alongside ("shift money from stocks to
T-Bills" when rates rise), so a rates curve moved valuations.

### 2. Futures — confirmed, no detail

Same NFO line is the only confirmation. No source describes margin, expiry, or
settlement. Treat as linear levered exposure to the underlying — the design must
fill in margining and expiry itself.

### 3. Bank loan + leverage — UNCONFIRMED for 2001, treat as inspired-by

No source found names a bank-loan button in WST 2001. What is confirmed: venture
starting stake, T-Bills, interest rates moving prices. The loan memory may come
from WST 98/99 or from the T-Bill/interest logic. Do NOT claim it as WST canon;
design it as our own margin feature on the `brokerage` banks.

### 4. Crew as leverage — confirmed, the forgotten amenity

- Brokers: required per exchange, without them little can be done.
- Analyst: trend prediction for a fee.
- Spy: rumors + insider info; hurts honesty, SEC watches.
- Lawyer: dodge fines, sue companies to push prices down.
- Press Agent: prestige campaigns, guru effect.
- Archivist: in-game glossary.
Hiring/firing your team was "your first stop as investment guru".

### 5. Information game — confirmed

News Centre ("most valuable source"), Pub (pick up AND spread rumors), Stakes
Screen (who holds what in the scenario). Cheatbook tips: let a scenario run a
week at high speed before buying, pause to 0 to pick; keep cash for advisor
fees; track max ~6 holdings in 1–2 sectors.

## Lesson from the GameSpot review (WST 2000, 7.1)

"The manual does a good job of explaining the interface, but has nothing to
outline the underlying logic of the game's virtual market." Prices moved
without visible cause even on easy settings. Our takeaway: every price-relevant
mechanic we add (basis, premium, margin, expiry) must be explainable in the
terminal — no inscrutable moves.

## Mapping to VirtualInvestor

```
  WST 2001               VirtualInvestor today
  ---------------------  ----------------------------------
  Shares                 order-intake + market-simulation (done)
  Futures                NEW: futures (this arc, chosen first)
  Calls/Puts             NEW: options (this arc, with futures)
  Bank loan              LATER: margin on brokerage banks
  Team (6 roles)         LATER: analyst/spy/lawyer/press agent
  News Centre            news-events + newsroom-narrator (done)
  Pub rumors             partial: news-events; spread mechanic missing
  Stakes Screen          partial: leaderboard + portfolio
  T-Bills/rates          MISSING entirely (idea for later)
  SEC/honesty            MISSING (pairs with spy crew)
```

## Arc order

Long-run goal is all of them, one change at a time. Chosen order: **(B)
futures + options first** (change `futures-options`), then bank-loan margin,
then crew. Rationale: derivatives reuse the existing book/journal/replay core;
margin changes `participant-risk-limits` loss bounds and deserves its own
deliberate scope; crew is mostly presentation + news plumbing and is cheapest
last.
