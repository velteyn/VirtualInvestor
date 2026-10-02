# Proposal

## Why

There is no open, self-contained market that lets a person experience realistic intraday trading against credible rivals. Real terminals show a live market but offer no game; training simulators replay history but do not react to the player. We want a playable, single-machine, persistence-backed intraday trading game whose market behaves like a real one — so the player's own size, speed, and reading of the tape matter.

## What Changes

- Introduce a deterministic, server-authoritative **market simulation** with a hybrid price model: a synthetic fundamental value (factor model + jumps, seeded from generated history) is wrapped by an order book and inventory-aware market making, so prices are believable and the player's orders can move them.
- Introduce a **launch pad** where the player sets starting capital, session length, and realism preset before a session begins.
- Introduce a **trading game session**: an intraday round with an opening auction, regular session, closing auction, and settlement; the player competes against five visible named rival bots while institutions act as the market.
- Introduce a **news wire** of fictional sources whose scheduled and random events move fundamentals, raise volatility, and drive differentiated bot reactions.
- Introduce **persistence and resume**: an append-only event log plus periodic snapshots in Postgres, enabling reconnect, server-restart recovery, and deliberate save/resume of a session.
- Define the **client/server boundary**: a deterministic Java core, a Spring Boot edge/game server, and a TypeScript terminal (openalgo-charts) that talks to the server through data and order feeds.

Explicit non-goals for this change:

- No persistent always-on world (design for it, but operate on-demand per session).
- No M&A, corporate raiding, or tycoon layer.
- No player-authored bots; only the bots we ship.
- No options, futures, or CFDs yet; instruments are a simulated NASDAQ-like equity universe.
- Single human player plus bots; additional human participants are future work.

## Capabilities

### New Capabilities

- `market-simulation`: The deterministic market core — instrument master (fictional NASDAQ-like equities + index), hybrid price formation over a latent fundamental value, order book and matching engine, market data (L1/L2, trades), trading sessions with opening/closing auctions, and market rules (tick/quantity grids, shorting, margin, halts).
- `trading-game-session`: The game layer — launch pad configuration (capital, duration, realism preset), round lifecycle, participant model (human, named rival bots, institutions), ranking and leaderboard, margin calls and settlement, and session resumption semantics.
- `news-events`: The news wire — fictional sources, event taxonomy (macro/sector/company/structure), scheduled and random delivery, rumor-before-confirmation staging, impact on fundamentals and volatility, and differentiated bot reactions.
- `persistence-resume`: Durable state — append-only event log, periodic world snapshots, read-model projections, player reconnect and reconciliation, server-restart recovery, and deliberate save/resume of a session.

### Modified Capabilities

- None. The project has no existing specs.

## Impact

- **New project code**: a Java 21 / Spring Boot server (Spring-owned edge, game services, and persistence; framework-free deterministic core) and a TypeScript browser terminal composed from `openalgo-charts`.
- **New runtime**: PostgreSQL (Docker Compose) for the event log, snapshots, and read models; optional Redis deferred until the leaderboard needs it.
- **Client/server contract**: a versioned wire protocol (JSON or protobuf) for market data, order entry, accounts, and news, plus reconnect/resync.
- **External references**: `openalgo-charts` is used as the terminal UI via its `DataFeed`, `OrderFeed`/`AccountFeed`, and `NewsFeed` contracts; a custom host adapts those to our server.
- **Determinism constraint**: the market core must be pure over (seed + event log); this is a hard rule that persistence, replay, and fairness all depend on.
