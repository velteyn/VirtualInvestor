# Proposal — News tab merge (change: news-tab-merge)

## Why

The dock has two news surfaces that confuse players: the News wire (headlines
for decisions) and the Newsroom (articles for reading), increasingly showing
the same content since wire rows upsert bodies in place. One tab with
expand-in-place full stories kills the duplication.

## What Changes

- Single **News** dock tab: the live wire as today (badges, priced-in flags,
  newest-first), where any row with a story body (length threshold as today)
  offers a "full story" affordance opening the complete article text in place
  (expanding row/popup — implementation choice, popup preferred for long
  bodies).
- The separate Newsroom tab is removed; its history load (late joiners read
  the session's story so far) moves into the News tab's initial load.
- **What is NOT in scope**: article generation, wire protocol, or feed
  semantics changes; the newsroom-narrator specs are untouched.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `terminal-trading-ui`: dock tabs (one News tab with inline full story, no
  separate Newsroom tab).
- `newsroom-narrator`: presentation merge only — articles still produced and
  archived as today; drop any requirement that mandates a *separate* tab
  (verify against the spec before writing the delta).

## Impact

- Web only: `newswire.ts` (+ expand/popup + history load), remove
  `newsroom.ts` tab mount, `terminal.ts` wiring.
- No server changes.
