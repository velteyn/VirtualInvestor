# Terminal

The browser terminal is a thin TypeScript app that mounts the `openalgo-charts` widget over
feeds adapted to the VirtualInvestor server. It lives in `web/`.

## Setup

```bash
cd web && npm install
cd web && npm run dev     # dev server on :5173, proxying /api and /ws to :8080
cd web && npm run build   # production bundle (typecheck + vite build)
```

The dev server proxies `/api` (REST) and `/ws` (WebSocket) to the Spring Boot edge on
`:8080`, so the browser sees one origin (`vite.config.ts`).

## Flow

1. **Launch pad** (`src/app/launchPad.ts`) — the player picks starting capital, session length
   and realism preset; `ServerClient.createRound` creates the round server-side.
2. **Terminal** (`src/app/terminal.ts`) — mounts `createWidget` from `openalgo-charts/widget`
   and wires the feeds.

## Adapters

The library owns the whole terminal; our job is only the four feeds it consumes
(`src/adapters/`):

| Adapter | Implements | Source |
|---|---|---|
| `VirtualInvestorDataFeed` | `DataFeed` (bars, depth, symbol search) | REST for search, WebSocket `bar`/`depth` frames; seeded placeholder history |
| `VirtualInvestorTradeFeed` | `OrderFeed` + `AccountFeed` (place/modify/cancel, order/position/account streams) | WebSocket `order`/`position`/`account` frames |
| `VirtualInvestorNewsFeed` | `NewsFeed` (rolling news buffer) | WebSocket `news` frames |
| `ServerClient` | REST + one multiplexed WebSocket, auto-reconnect | — |

The wire payload shape is documented in `docs/wire-protocol.md`; the adapters are the only
files that know it.

## Important note on the write path

`openalgo-charts`' `OrderEngine` is optimistic UI; the **server is the authoritative book**. The
adapter's `place`/`modify`/`cancel` currently acknowledge optimistically, and the server's
`order`/`position` frames are what reconcile the terminal. Sending order commands over the wire
(and the server-side handlers for them) is the next step; until then the terminal renders live
market data and account state but does not yet push orders to the server.

## Not yet implemented

- Order-entry commands from the terminal to the server (wire + handlers).
- Wiring the news panel and leaderboard panel to the live data in the widget chrome.
- Hosting-on-one-origin production reverse proxy config (dev uses the Vite proxy).
