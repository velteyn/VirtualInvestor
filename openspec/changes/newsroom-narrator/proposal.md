# Proposal

## Why

Headlines move prices, but nobody writes the stories: the news wire is bare
one-liners ("NVDO beats quarterly expectations") with empty bodies. A narrator
turns releases into short articles — an earnings beat becomes a readable story
grounded in the company's profile — and gives the terminal a newsroom tab worth
opening. It also answers the "game master" question from exploration: the
machine already simulates the world; what it lacks is a voice describing it.

## What Changes

- A decoupled narrator plugin that subscribes to news releases (plus round
  start/end markers) and writes short articles out-of-band via an external
  LLM sidecar (llama.cpp, ~1B model for the i5/8GB reality).
- Article bodies ride the existing news wire (`NewsDto.body`, today always
  empty — zero protocol change) and persist in a new articles table so late
  joiners can read the session's story so far.
- A newsroom tab in the terminal listing the round's articles.
- Degradation ladder, worst case first: sidecar down → template articles built
  from headline + profile; templates off → today's bare headlines. The market
  never waits for prose.

## Capabilities

### New Capabilities

- `newsroom-narrator`: event-driven article generation, persistence, and the newsroom tab.

### Modified Capabilities

(none)

## Impact

- New `server/narrator` module (interface + llama.cpp client + template
  fallback + disabled stub); `edge` gains an articles endpoint and fills
  `NewsDto.body` from completed articles; `persist` gains the articles table;
  `web` gains the newsroom tab following the company-tab pattern.
- Determinism untouched: the narrator consumes releases after the fact, never
  feeds the engine, and bots keep reading `NewsSignal`s only — articles are
  content, not simulation input.
- Ops: sidecar is opt-in and external (llama.cpp server mode); default config
  runs everything else with headlines as today.
