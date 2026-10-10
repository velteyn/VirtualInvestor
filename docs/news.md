# News and events

The news wire is the heartbeat of a round. It moves fundamental values, raises volatility, and
gives the rivals something to react to. This document describes the implemented model in
`com.virtualinvestor.core.news`.

## Sources (all fictional)

`NewsSource` has three fictional outlets: **THE_WIRE**, **MERIDIAN_NEWS**, **TAPE**.
`NewsSource.fromToken` refuses any other name, and `FictionalNames` rejects real company or
outlet names in headlines and bodies, so a real name can never enter the market.

## Taxonomy

`NewsScope` classifies an event by what it moves:

| Scope | Impact (`FundamentalModel.applyNews`) |
|---|---|
| `COMPANY` | the named instrument only |
| `SECTOR` | every instrument in the named sector |
| `MACRO` | every instrument, scaled by its market beta |
| `STRUCTURE` | the named symbol (reserved for market-structure events) |

Each item carries a `direction` (-1, 0, 1), a non-negative `magnitude`, and a `durationTicks`
describing whether its effect is a pop or a drift. The signed impact is
`direction * magnitude`.

## Generation

`NewsGenerator` builds a round's timeline from the round seed:

- one scheduled **earnings** event per equity (scope `COMPANY`, stage `CONFIRMED`) at a fixed
  fraction of the session that spreads across names;
- random events whose count is `sessionTicks / ticksPerRandomEvent`, choosing a scope by a
  weighted roll (company-heavy, then sector, then macro), a direction, a magnitude, and a
  duration.

All draws come from the `news.random` stream, so the same seed yields identical events at
identical ticks. The event count scales with session length.

## Staging: rumor, confirm, deny

`PlannedNews` stages a story over time and `NewsEngine` applies it:

1. at the rumor tick, the fundamental moves by `signedMagnitude * rumorFraction` and a
   `NewsReleased` event with stage `RUMOR` is emitted;
2. at the resolution tick, a confirmed story applies the remaining
   `signedMagnitude * (1 - rumorFraction)` and emits `NewsConfirmed`; a denied story reverses
   the rumor move and emits `NewsDenied`.

## Impact on volatility

`VolatilityState` holds a per-instrument multiplier (1.0 = calm). When significant news is
applied, `NewsEngine` raises the multiplier for every affected instrument by the magnitude of
the move; the multiplier decays each step. `FundamentalModel` scales idiosyncratic diffusion
by the multiplier, so realized volatility rises after a shock, and `MarketMakerStrategy` quotes
a wider spread as the volatility input rises.

## Events emitted

- `NewsReleased` (stage `RUMOR`)
- `NewsConfirmed`
- `NewsDenied`

## Who reads headlines

Releases are folded into per-symbol signals each tick and handed to strategy
contexts (`BotContext.news`, null on quiet ticks):

- **VULTURE** (`NewsHunterStrategy`, 40 shares) pounces on any signal above 0.01
  the same tick it is released — the fastest gun in the room.
- **MERIDIAN** (`MacroReactionStrategy`, 1,000 shares) reads `MACRO` signals only,
  after a 4-tick delay: size arrives once the tape confirms.
- Company releases reach their symbol, sector releases their members, macro (and
  structural) releases the index only — one headline can never fire every engine.
- Every bot reads a slightly different magnitude (±25%, seeded per headline and
  reader, replay-identical), so borderline headlines split the crowd instead of
  moving it in lockstep.

Rumor versus resolution: bots trade the headline (the rumor piece). The
resolution plays out through fundamentals and volatility only — hunters who
bought a denied rumor eat the loss, and both desks run lifetime-negative on
news flow in the baselines.

## Newsroom narrator

Headlines gain story bodies from the `server/narrator` module: the edge
re-broadcasts the same news id with the body filled once the article is ready,
and the terminal upserts it in place (no duplicates) with a "full story"
marker. Articles persist in the `articles` table and are served to late joiners
via `POST /api/rounds/articles`. Bots never read articles — `NewsSignal`s stay
the only machine-readable news — and the simulation never waits for prose.

Degradation ladder: sidecar LLM → template bodies (headline + subjects, no
model) → bare headlines. Every headline is article-eligible except random
company noise; earnings, sector, macro and structural releases are narrated up
to a per-round cap of 40 with skip-on-overflow. Round preview and recap are
reserved kinds outside the selection policy.

### Bringing up the sidecar (opt-in)

The sidecar is a dockerized llama.cpp server, an integral part of the
project — no local install needed:

1. Fetch the weights once (install phase, ~640 MB, gitignored):
   `infra/narrator/fetch-model.sh`. Default is Qwen2.5-0.5B-Instruct Q8, the
   smallest usable instruct model in the families from exploration; it writes
   a 150-word story in ~20s on the reference i5/8GB box. (For richer prose,
   swap `MODEL_FILE`/`MODEL_URL` to the 1.5B sibling.)
2. `docker compose --profile narrator up -d` from `infra/` (the entrypoint
   refetches if the weights are missing). The server listens on
   `127.0.0.1:18080` — localhost only, like the db. Container port is 8081,
   not 8080: some hosts filter outbound traffic to container port 8080.
3. Start the edge with `VI_NARRATOR=llama` (optional `VI_NARRATOR_URL`,
   `VI_NARRATOR_TIMEOUT_MS`, `VI_NARRATOR_MODEL` label for the articles table).
4. Verify: await a release, then check the article lands — the headline's row
   gains "full story" in the terminal, and the articles endpoint lists it
   with the model label. With the sidecar down, headlines still publish
   instantly and templates stand in; with `VI_NARRATOR=disabled`, the wire
   carries bare headlines exactly as before the newsroom existed.

The prompt pins the 21 fictional names and forbids real companies, outlets and
advice; every body passes a denylist post-filter that drops on hit, leaving
the headline standing alone. No player input ever enters a prompt.

## Not yet implemented

- Human-facing delivery cadence and fairness (spec: fair human cadence) — arrives with the
  edge/terminal wiring.
- `STRUCTURE`-scope generators (halt/squeeze/index-rebalance events).
- Scheduled non-earnings events (macro calendar) known in advance.
