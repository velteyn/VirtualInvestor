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

| `type` | Fields | Direction |
|---|---|---|
| `hello` | `protocolVersion` | server → client |
| `error` | `code`, `message` | server → client |
| `bar` | `symbol`, `interval`, `time`, `open`, `high`, `low`, `close`, `volume` | server → client |
| `depth` | `symbol`, `bids[{price,qty}]`, `asks[{price,qty}]`, `ltp`, `tick` | server → client |
| `trade` | `symbol`, `price`, `quantity`, `aggressor`, `tick` | server → client |
| `order` | `orderId`, `participantId`, `symbol`, `side`, `type`, `quantity`, `price`, `triggerPrice`, `status`, `filledQty`, `tick` | both |
| `position` | `participantId`, `symbol`, `netQty`, `avgPrice` | server → client |
| `account` | `participantId`, `cash`, `realizedPnl` | server → client |
| `news` | `newsId`, `source`, `scope`, `symbol`, `sector`, `headline`, `body`, `signedMagnitude`, `durationTicks`, `stage`, `tick` | server → client |
| `leaderboardEntry` | `rank`, `participantId`, `displayName`, `equity`, `startingCapital`, `returnFraction` | server → client |
| `symbol` | `ticker`, `name`, `sector`, `assetClass` | server → client |
| `round` | `roundId`, `seed`, `startingCapital`, `durationMinutes`, `preset`, `state`, `sessionToken` | both |

The Java core's record shapes are **not** the wire contract; `WireMapper` translates between
them so a core change cannot silently reshape the protocol.

## REST endpoints (v1)

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/api/rounds` | `{startingCapital, durationMinutes, preset}` | `round` (`201`) |
| POST | `/api/symbols/search` | `{query, limit}` | `symbol[]` |
| POST | `/api/rounds/leaderboard` | `{roundId}` | `leaderboardEntry[]` |

Errors: `400` for invalid configuration or an unsupported version, `404` for an unknown round.

## WebSocket behaviour

- On connect the server sends `hello`.
- Market depth is coalesced per symbol within ~250 ms, so a fast book does not flood clients.
- An undecodable client message returns an `error` with code `bad_message`; a decoded message
  that has no handler yet returns `not_implemented` (write handlers arrive with the terminal).

## Not yet implemented

- Order-entry command messages and their handlers (Group 11 adapters).
- Authentication on REST/WebSocket beyond the round's session token.
- A generated schema artifact (the payloads are hand-written records today).
