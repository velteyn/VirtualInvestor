# Tasks — Dead market makers

## 1. Engine guards

- [x] 1.1 `MatchingEngine`: reject LIMIT orders with non-finite or
  non-positive limit prices; unit tests (negative, zero, NaN rest nothing).
- [x] 1.2 `BotRunner.refreshQuote`: skip invalid-price rungs; unit test with
  a sub-tick reference (far-OOM premium) posts no quote.

## 2. Total fill accounting

- [x] 2.1 `RunningRound.creditFill`: fee notional clamped, never throws;
  runtime regression test: stepped round with quoted far-OOM series produces
  fills/prints and no exception.

## 3. Integration

- [x] 3.1 `mvn -pl core,runtime test`, web untouched, `openspec validate`
  green; fresh-round live playtest (market buy fills, prints stream).
