# Design

## Context

See proposal.md (Why). Current state, verified in code:

- `server/core/src/main/resources/instruments.csv` holds 21 instruments (20 equities + VNDX index) with ticker, name, sector, shares, float, reference price, base volatility, and betas. No descriptions exist anywhere.
- The terminal mounts tab-style components into slots (`mountPortfolio` into the positions strip, `mountTicket` into the right column); `.vi-tabs` styling exists. `mountTerminal` is the single wiring point.
- Content is fully static: nothing about a company changes within or across rounds, so no endpoint, no polling, no state.

## Goals / Non-Goals

**Goals:**

- One honest, readable profile per instrument, consistent with its universe row and its news headlines.
- A company tab beside the existing terminal tabs, working offline from the round (no server calls after load).

**Non-Goals:**

- No live data in the tab (no current price, no positions — the terminal already shows those live elsewhere; duplicating them invites staleness bugs).
- No server changes, no new endpoints, no persistence.
- No LLM at runtime: descriptions are authored content, reviewed once, committed as data.

## Decisions

**1. Ship profiles as a static web asset (`web/src/assets/companies.json`), not a server resource or endpoint (over extending `instruments.csv` or adding an endpoint).**

CSV cells with prose need quoting discipline and a parser change; an endpoint adds a round trip for data that never changes. A JSON asset imports directly, versions with the client, and works with zero server involvement. The universe CSV stays the simulation's source of truth; the asset mirrors its static columns plus `description`.

**2. Follow the portfolio-tab mounting pattern (over a new tab framework).**

A `mountCompany(strip, companies)` component with its own symbol selector, wired in `mountTerminal` next to portfolio/ticket. Same lifecycle (`destroy()`), same theme classes, same phone behavior. No new architecture — the fourth tab looks like the third.

**3. Author descriptions once, review against three consistency rules (over generating per round).**

Each description must (a) contain no real company names (FictionalNames rules apply to content too), (b) agree with its sector and reference-price scale (no "scrappy startup" at $640 with 1.5B shares out), and (c) give the news generator's headline templates plausible subjects (earnings beats need a business that earns). Reviewed in the tasks' verification step, not by code.

## Risks / Trade-offs

- [Asset and universe drift apart (a retuned CSV orphans a description)] → Mitigation: a task verifies every asset ticker exists in the universe with matching sector and reference price; the check is cheap enough to keep as a build-time assertion if desired.
- [Descriptions duplicate what the newsroom narrator will later write] → Mitigation: intentional layering — profiles are the stable "about" page, articles are events. The narrator change can link them; neither blocks the other.
- [21 hand-written descriptions vary in quality] → Mitigation: one voice, one length band (3–5 sentences), reviewed together in a single pass.

## Migration Plan

Content + client only. Rollback is deleting the asset, the component, and three wiring lines. Ship order: content asset with consistency check → tab component + styling → terminal wiring → build + visual check. Each step keeps the build green on its own.
