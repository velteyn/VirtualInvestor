# Terminal

The browser terminal is a thin TypeScript app that mounts the `openalgo-charts` widget over
feeds adapted to the VirtualInvestor server. It lives in `web/` and is fully tradeable: the
chart ticks live and orders placed in the terminal reach the round over the same socket.

## Setup

```bash
cd infra && docker compose up -d db        # persistence database (vi-db)
cd server && mvn -o -pl core install -DskipTests   # keep the installed core jar fresh
# run the edge (from the repo root):
java -cp "server/edge/target/classes:server/runtime/target/classes:$(cat /tmp/vi-cp.txt)" \
  com.virtualinvestor.edge.EdgeApplication  # :8080
cd web && npm install
cd web && npm run dev     # dev server on :5173, proxying /api and /ws to :8080
cd web && npm run build   # production bundle (vite build)
```

The dev server proxies `/api` (REST) and `/ws` (WebSocket) to the Spring Boot edge on
`:8080`, so the browser sees one origin (`vite.config.ts`).

> Only run one live round per server while trading: broadcast frames carry no `roundId`,
> so a second live round's trades would appear on the first terminal. Pause stale rounds
> with `POST /api/rounds/control` (`{roundId, action: "pause"}`).

## Flow

1. **Launch pad** (`src/app/launchPad.ts`) — the player logs in (or registers with
   a bank and waits for approval), then picks starting capital, session length
   and realism preset; `ServerClient.createRound` creates the round server-side.
2. **Terminal** (`src/app/terminal.ts`) — mounts `createWidget` from `openalgo-charts/widget`,
   joins the round over the socket with the login token, and wires the feeds plus
   the trade overlays (`TradeController.reconcile` from the trade tier).

## Adapters

The library owns the whole terminal; our job is only the feeds it consumes
(`src/adapters/`):

| Adapter | Implements | Source |
|---|---|---|
| `VirtualInvestorDataFeed` | `DataFeed` (bars, depth, symbol search) | `POST /api/rounds/history` warm-up bars for `getBars`; live `bar`/`depth` frames |
| `VirtualInvestorTradeFeed` | order/position/account mirror for the overlays | live `order`/`position` frames + `account` snapshots; `reset()` on reconnect |
| `VirtualInvestorNewsFeed` | `NewsFeed` (rolling news buffer) | WebSocket `news` frames |
| `ServerClient` | REST + one multiplexed WebSocket, auto-reconnect with rejoin + resync | — |

The wire payload shape is documented in `docs/wire-protocol.md`; the adapters are the only
files that know it.

## Order path (read this before touching it)

- Outbound: the ticket is the single transmit path (`ServerClient.placeOrder/
  modifyOrder/cancelOrder` send `placeOrder`/`modifyOrder`/`cancelOrder` frames;
  `orderType`, not `type` — `type` is the frame discriminator). Chart menus and
  Buy/Sell buttons only prefill the ticket; drag-to-modify calls `modifyOrder`
  directly. `GameWebSocketHandler` routes commands to `RoundRuntimeService.submit`
  for the round the socket joined. Commands apply at the next tick.
- Inbound: every `order` frame is a self-contained execution report — `side`,
  `orderType`, `quantity`, `price` plus `filledQty`, uppercase `status`
  (`WORKING`/`PARTIAL`/`FILLED`/`CANCELLED`/`REJECTED`) and the rejection
  `reason`. Terminal states delete the working-order line; reasons display
  inline on the ticket with typed values kept. Every fill also streams
  `position` and `account` frames, so holdings, P&L and cash move live —
  resync is only needed after a dropped connection.
- Reconnect: the client re-sends `joinRound` + `resync`; the server replays
  working orders, positions and account as ordinary frames; `tradeFeed.reset()`
  clears the mirror first.

## Order ticket

Below news in the right column: blue/red side toggle, type selector (price
fields gate per type), quantity with remembered default plus 10/50/100/Max
(Max = buying power over price, floored), limit/stop prefilled from the touch
(ask for buys, bid for sells, last mark when the book is empty) and following
it until edited. The preview shows cost + bank fee with cash left and disables
Transmit over buying power. Login flow: register (bank required) → approval →
login → session config; admin-created logins force a password change first.

## Portfolio tab and the resume guarantee

The bottom dock has Positions and Portfolio tabs. Portfolio shows the account
header (equity + return, cash, buying power, unrealized, realized, fees),
a holdings table (`Symbol | Qty | Avg | Last | Value | Invested | Unreal $ |
Unreal % | Realized`) with per-row Close, working orders with Modify/Cancel,
and a History section folded from the round journal (`POST /api/rounds/fills`).
History loads on mount, refreshes on reconnect, and refetches whenever one of
the player's orders reaches a terminal state — a fill that lands while watching
appears without touching Reload.
Every number derives from server state, so closing the browser loses nothing:
on return, resync replays positions/account/orders and the fills query shows
what executed while away — including per-symbol realized on closed legs.

Return-after-absence drill (scripted in `/tmp/vi-pf-e2e.mjs`, 8/8 on
2026-10-06): buy 10 NVDO @ 634.03 while watching, close the socket, sell all @
633.99 from a second socket, return on a fresh socket — resync shows the flat
position, the account frame carries `realizedPnl: -0.40` matching fill
economics to the cent, and history lists both fills.

## Reading the logs

- Browser console (F12 → Console): every line starts with `[vi]` — socket
  open/close, joins (with or without token), resyncs, transmitted orders,
  order frames for your own orders, account updates, mirror resets, and every
  server error frame. If an order does nothing, the console says which hop
  dropped it.
- Server log: `PlayerController` logs register/login/refusals (never
  passwords), `RoundController` logs round creation with the linked player,
  `AdminController` logs admin logins/approvals/resets, and
  `GameWebSocketHandler` logs joins bound or refused with the reason,
  routed order commands, and resync frame counts.

## Run steps (reproduces a tradeable terminal)

Server + web as in Setup, then either in the browser or scripted over `/ws`:

1. Create a round (launch pad, or `POST /api/rounds` with `X-API-Version: 1`).
   A 15-minute round reaches continuous trading ~60 s after creation (schedule is 5%
   pre-open + 2% opening auction); orders placed before that are rejected.
2. Open the socket, wait for `hello`, send `joinRound`.
3. Watch `trade` frames tick.
4. Place a far-from-touch `LIMIT` order → an `order` frame with `status: WORKING` and the
   working-order line appears.
5. Place a small `MARKET` order → `TRADE` + `order` frame with `status: FILLED`.
6. `cancelOrder` the resting limit → `status: CANCELLED`, the line disappears.
7. Send `resync` → working orders, positions and `account` replay as frames.
8. Send an offsetting `MARKET` order to flatten; resync shows no `position` frame.

These steps are scripted in `/tmp/vi-e2e.mjs` (round create → history → WORKING →
FILLED → CANCELLED → resync → flat); it passed 11/11 against the live edge on
2026-10-03. The ticket flow is scripted in `/tmp/vi-tk-e2e.mjs` (sized limit
rests with quantity, off-tick reject carries its reason, cancel lands); 3/3 on
2026-10-05. In the browser: log in, open the ticket, set 50 @ touch, Transmit —
the working line appears; drag it to modify; cancel from the Portfolio tab.
