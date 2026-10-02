# VirtualInvestor

A self-contained, realistic intraday trading game. A deterministic market simulation
runs on a Java server; the player sets up a session on a launch pad, then trades a
live market against five named rival bots while institutions provide the market.
Sessions are durable and resumable.

Planning lives in `openspec/changes/virtualinvestor-foundation/` (proposal, specs,
design, tasks).

## Prerequisites

- Java 21 (JDK) and Maven 3.9+
- Node.js 20+ and npm
- Docker with the Compose plugin (for PostgreSQL)

## Module map

```
server/            Maven multi-module server
  core/            deterministic, framework-free market simulation
  game/            round lifecycle, launch pad, ranking, settlement
  persist/         event log, snapshots, read-model projections
  edge/            Spring Boot REST + WebSocket edge
web/               TypeScript terminal (Vite + openalgo-charts)
infra/             docker-compose.yml (PostgreSQL)
openspec/          planning artifacts for this project
```

## Development commands

Start the database (required by the server):

```bash
cd infra && docker compose up -d db
```

Build and test the simulation core:

```bash
cd server && mvn -pl core test
```

Build the whole server reactor:

```bash
cd server && mvn test
```

Install and build the terminal:

```bash
cd web && npm install
cd web && npm run build
```

Run the terminal dev server:

```bash
cd web && npm run dev
```

## Status

Foundation under construction. See `openspec/changes/virtualinvestor-foundation/tasks.md`
for the implementation checklist.
