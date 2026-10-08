# Wire protocol

The contract between the terminal and the Java server. Implementation lives in the `edge`
module: `com.virtualinvestor.edge.wire.WireMessage`, `com.virtualinvestor.edge.api.WireMapper`.

## Transport

- **REST** (`/api/...`) for request/response: round control, symbol search, leaderboard, history.
- **WebSocket** (`/ws`) for streaming: market data, order/account events, and news.

Both use JSON. The protocol version is `"1"` (`WireMessage.PROTOCOL_VERSION`); the server sends
a `hello` with it on WebSocket connect.

## Versioning

REST endpoints are versioned with Spring MVC API versioning
(`@RequestMapping(version = "1")`), driven by the `X-API-Version` header
(`spring.mvc.apiversion.use.header`). Versioning is required, so a request without the header is
rejected with `400`. The WebSocket stream is versioned by the `hello` message.

## Payloads

Every payload carries a stable `type` discriminator. The set (design D7):

> The order's own kind travels as `orderType`, never `type`: `type` is the frame
> discriminator (`"order"`), and a second `type` key would collide with it and hide the
> frame kind from the client. The same rule applies to `placeOrder` (`orderType`).

| `type` | Fields | Direction |
|---|---|---|
| `hello` | `protocolVersion` | server → client |
| `error` | `code`, `message` | server → client |
| `bar` | `symbol`, `interval`, `time`, `open`, `high`, `low`, `close`, `volume` | server → client |
| `depth` | `symbol`, `bids[{price,qty}]`, `asks[{price,qty}]`, `ltp`, `tick` | server → client |
| `trade` | `symbol`, `price`, `quantity`, `aggressor`, `tick` | server → client |
| `order` | `orderId`, `participantId`, `symbol`, `side`, `orderType`, `quantity`, `price`, `triggerPrice`, `status`, `filledQty`, `reason`, `tick` | server → client |
| `fill` | `orderId`, `symbol`, `side`, `orderType`, `quantity`, `filledQuantity`, `price`, `fee`, `tick` | server → client |
| `markout` | `orderId`, `symbol`, `side`, `fillPrice`, `markPrice`, `markout`, `fillTick`, `markTick` | server → client |
| `placeOrder` | `clientToken`, `symbol`, `side`, `orderType`, `quantity`, `price`, `triggerPrice` | client → server |
| `modifyOrder` | `clientToken`, `orderId`, `price`, `triggerPrice` | client → server |
| `cancelOrder` | `clientToken`, `orderId` | client → server |
| `joinRound` | `roundId`, `participantId`, `sessionToken` (approved login token, required) | client → server |
| `resync` | `participantId`, `sessionToken`, `lastSeenSeq` | client → server |
| `position` | `participantId`, `symbol`, `netQty`, `avgPrice` | server → client |
| `account` | `participantId`, `cash`, `realizedPnl`, `feesPaid` | server → client |
| `news` | `newsId`, `source`, `scope`, `symbol`, `sector`, `headline`, `body`, `signedMagnitude`, `durationTicks`, `stage`, `tick` | server → client |
| `leaderboardEntry` | `rank`, `participantId`, `displayName`, `equity`, `startingCapital`, `returnFraction` | server → client |
| `symbol` | `ticker`, `name`, `sector`, `assetClass` | server → client |
| `round` | `roundId`, `seed`, `startingCapital`, `durationMinutes`, `preset`, `state`, `sessionToken` | both |

The Java core's record shapes are **not** the wire contract; `WireMapper` translates between
them so a core change cannot silently reshape the protocol.

## REST endpoints (v1)

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/rounds` | `{startingCapital, durationMinutes, preset, sessionToken?}` | `round` (`201`) |
| POST | `/api/players/register` | `{username, password, bankId}` | player (`201`, `PENDING`) |
| POST | `/api/players/login` | `{username, password}` | login + `sessionToken` (`200`; `403` pending, `401` bad credentials) |
| POST | `/api/players/banks` | `{}` | bank[] with fee terms |
| POST | `/api/admin/login` | `{password}` (master, env) | `{adminToken}` (later calls use `X-Admin-Token`) |
| POST | `/api/admin/players` | `{}` | player rows |
| POST | `/api/admin/players/approve|reject` | `{playerId}` | player row |
| POST | `/api/admin/players/reset-password` | `{playerId, newPassword}` | `200` (sessions invalidated) |
| POST | `/api/admin/players/reset-purse` | `{playerId, startingCapital}` | `200` |
| POST | `/api/admin/rounds` | `{}` | round rows (`roundId`, `state`, `tick`) |
| POST | `/api/admin/rounds/pause|resume|settle` | `{roundId}` | `200` |
| POST | `/api/symbols/search` | `{query, limit}` | `symbol[]` |
| POST | `/api/rounds/leaderboard` | `{roundId}` | `leaderboardEntry[]` |
| POST | `/api/rounds/history` | `{roundId, symbol}` | warm-up `bar[]` |
| POST | `/api/rounds/fills` | `{roundId, participantId}` | `fill[]` (one row per filled order) |
| POST | `/api/rounds/markouts` | `{roundId, participantId, horizonTicks?}` | `markout[]` (fill price vs first print at/past fill tick + horizon; default horizon 5 ticks) |
| POST | `/api/rounds/control` | `{roundId, action}` (`pause`/`resume`) | `204` |

Errors: `400` for invalid configuration or an unsupported version, `404` for an unknown round.

## Order frames

Each `order` frame is a self-contained execution report (FIX `ExecutionReport`-style): it
carries the order's full identity (`side`, `orderType`, `quantity`, `price`) plus its running
fill state (`filledQty`, `status`). `status` is an uppercase `OrderStatus` name —
`WORKING`, `PARTIAL`, `FILLED`, `CANCELLED`, `REJECTED` — so the terminal can rebuild its
working-order book from the stream alone. Terminal states (`FILLED`, `CANCELLED`,
`REJECTED`) drop the line; `WORKING`/`PARTIAL` (re)draw it.

## WebSocket behaviour

- On connect the server sends `hello`.
- A socket must `joinRound` with an approved login token before sending order commands;
  otherwise it gets `not-approved` (bad/foreign token) or `not_joined` (no bind yet).
  The bind follows the token's seat (`human`, `human-2`, …), never the stated
  participant id; tokens without a seat in that round get `not-approved`.
- Order commands are idempotent by `clientToken` and apply at the next tick (≤1 tick).
- Market depth is coalesced per symbol within ~250 ms, so a fast book does not flood clients.
- On reconnect the client re-sends `joinRound`, then `resync`; the server replays the
  participant's working orders, non-flat positions and account as the same frames the live
  stream emits. The client clears its mirror first, so the terminal reconciles.
- Broadcast frames carry no `roundId`: run one live round at a time per server (pause stale
  rounds via `/api/rounds/control`), or clients will see each other's markets.
- An undecodable client message returns an `error` with code `bad_message`; a decoded message
  with no handler returns `not_implemented`.

## Not yet implemented

- Self-service recovery (no email): locked-out players go through the game master.
- A generated schema artifact (the payloads are hand-written records today).
