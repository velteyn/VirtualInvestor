# Proposal

## Why

WST98's reverse-engineered reports confirm its market is a scripted dice tree with no order book — nothing to import there. What is worth importing is its legible character layer (17 faces parameterized by a few floats, 3 real traders) and its news craft (paired branch headlines, 9-type template packs, day-windowed banter). Our bots (`docs/bots.md`: MARK/TIDE/LEDGER/BLITZ/VULTURE) are functional archetypes with no personality, and our narrator stages RUMOR → CONFIRMED/DENIED from a single headline, leaving drama on the table.

## What Changes

- Give each bot a **dispersion** (misperception) parameter scaling its news signal as `(1 − 0.01 × dispersion)` (WST98 `FUN_00469FD4` homage), plus a **reliability tier** on news sources (wire vs patron vs spy) with different confirm probabilities.
- Author **branch-headline triples** per story (rumor-lede + confirm-lede + deny-lede) so resolution stages read as drama, not repetition; `TemplateNarrator` picks by stage deterministically.
- Add **template packs per scope/stage** (takeover/regulatory/guru/analyst-color/taunt) with `{0}`/`{1}` positional slots and ≤200-char bodies, plus a day-windowed taunt pack for room banter.
- Name the cast after the WST98 seventeen (lore only, fictional use); WST98 real nouns (OPEC, Iraq, King Fahd) stay out via existing `FictionalNames`.
- Out of scope: `Dbrelat` contagion import, corrupt-analyst bias mode, gatekept prestige content, TakeoverBid settlement event — each a named follow-up.

## Capabilities

### New Capabilities

- None — behavior lands on existing capabilities below.

### Modified Capabilities

- `news-events`: differentiated bot reactions gain dispersion scaling + source reliability tiers; rumor/confirm/deny gains staged headline triples and scope template packs.
- `newsroom-narrator`: article generation gains branch-headline selection by stage and scope template packs (template path; sidecar prompt carries prior-beat context unchanged).

## Impact

- Core: `com.virtualinvestor.core.news` (generator, engine signals), `com.virtualinvestor.core.bot` (strategy contexts, hunter/macro reads), `server/narrator` templates + prompt.
- Determinism: all draws stay on seeded streams (`news.random` + per-reader splits); replay-identical.
- Terminal: News tab shows paired ledes per stage; no wire-protocol break (same `NewsReleased/Confirmed/Denied` shapes, new text fields only).
