# Wall $treet Trader 2001 — inspiration notes

Research reference for the derivatives/leverage arc. Written Oct 2026 from
secondary sources because **no official WST 2001 manual PDF is publicly
archived** (checked archive.org, oldgamesdownload, MobyGames, PC Gamer scans,
GameFAQs). **Updated with primary-source extracts from the game disc itself**
(`WST2001.iso` from the user's archive): the full in-game tutorial script
(`INSTALL/TUTO.SRC`), all help bubbles (`INSTALL/HELPBUB.SRC`), and the tips
file (`INSTALL/TIPS.SRC`). No manual file ships on the disc — the readme says
*"Ce fichier contient des informations qui ne se trouvent pas dans le manuel"*,
confirming a printed manual existed separately.

## Primary-source mechanics (verbatim from the disc)

### Futures: 20% margin, broker auto-settles

- *"Quand vous achetez un future... vous n'avez à payer que 20 % maintenant...
  C'est ce qu'on appelle un effet de levier."* → **5x leverage**, hardcoded 20%.
- *"Quand les futures arrivent à échéance... votre broker le fait
  automatiquement pour vous."* → auto-settlement at expiry, exactly our
  settlement design (D6).

### Options: ~10% premium, selectable strike AND expiry

- *"l'option ne représente en général que 10% du prix de l'action... l'effet
  de levier pour les options est environ deux fois plus important que
  [futures]"* → **~10x leverage for long options**.
- Help bubbles: *"Appuyez ici pour fixer l'échéance"* and *"fixer le prix
  d'exercice"* → the original had **selectable expiries and strikes**, not our
  one-expiry simplification. Keep single-expiry for v1; multi-expiry is a
  named follow-up.
- *"si vous vendez une option d'achat ou de vente, vous ne pouvez revenir sur
  votre décision, les risques sont donc élevés"* → **short options are binding**,
  reinforcing our always-margin-covered-shorts rule.
- Tips file: *"L'emploi d'options et de future est essentiel pour gagner.
  Utilisez un délai court pour réinvestir les bénéfices."* → short expiries as
  the core loop; supports intraday-only scope.

### Fees: percentage + minimum (matches our brokerage spec)

- *"Voici le montant que vous payez pour chaque opération, sous forme de
  pourcentage de la transaction totale"* + *"Voici le montant minimum... Si la
  commission n'excède pas le coût minimum, vous payez uniquement cette
  commission forfaitaire."* → `max(minimum, rate x notional)`, same shape as
  `brokerage`, except percentage-based rather than per-share. Consider
  percentage commissions for derivatives books.

### No bank loans: "la banque" is the team HQ

- The tutorial's bank visit covers only hiring/managing employees; the PDA's
  cash/net-worth display lives on the trading screen, and no tutorial, tip, or
  help bubble mentions borrowing, interest, or debt. **The loan memory is not
  from this game** — the arc's margin feature should be designed as our own
  invention (or dropped), not as WST canon.

### Crew details beyond the NFO summary

- Brokers have happiness/stress/health; sad brokers are less efficient and may
  quit; fixed with gifts, raises, insurance. Prestige + honesty affect
  well-being; candidates state honesty/well-being expectations on their CVs.
- Analyst also raises the player's finance skill and writes performance
  reports (*"...vous aidera à comprendre, si nécessaire, où vous avez
  échoué"* — a proto-AI-Coach).
- Guru effect: exact predictions raise prestige, and prestige makes future
  predictions move prices more — a feedback loop.
- Spy needs the press agent to spread rumors; using a spy means hiring a
  lawyer. Rumor pub patrons are unreliable vs the analyst.
- Stakes screen: ownership share → board votes → decisions that move the
  price (takeover angle, out of scope for derivatives).

### Interface details worth stealing

- PDA shows date, cash, net worth; tabs for shares/futures/calls/puts, owned
  vs in-process orders; volume gauges as % of wealth; total cost preview.
- NewsCenter has a pause clock for reading news in real-time games.
- Mouse wheel scrolls PDA lists; advanced order types exist but are
  manual-only (*"Les ordres avancés sont expliqués dans le manuel du jeu"*).
- Phone to chat with other players (multiplayer lobby feature).

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
futures + options first** (change `futures-options`), then an original
margin feature (no WST canon — see "No bank loans" above), then crew.
Rationale: derivatives reuse the existing book/journal/replay core;
margin changes `participant-risk-limits` loss bounds and deserves its own
deliberate scope; crew is mostly presentation + news plumbing and is cheapest
last.
