# Proposal

## Why

The game is single-human today: every socket plays as a hardcoded `human` with no
identity, no memory between rounds, and no costs — while the roadmap is multiplayer
with persistent players, a game-master backyard, and fictional banks whose broker
fees simulate real trading costs. Without identity and a purse, there is nothing to
persist, nothing to charge fees against, and no fair way to admit more humans.

## What Changes

- Player registration with username + password (no email; no verification or
  recovery flow — the game master resets passwords instead) and login that binds
  the socket's participant to the registered identity. Unapproved logins are
  rejected; registration gains a pending-approval state.
- A game-master backyard on a separate `/admin` surface with its own login
  (master password from the environment, never in the repo): approve/reject
  players, reset passwords, reset purses, list/pause/resume/settle rounds.
  Player tokens never authorize admin calls and vice versa.
- Persistent player purse: cash + holdings survive in the database and load at
  round start / settle back at round end. Rounds keep working exactly as today;
  only where the money comes from and goes back to changes.
- Fictional banks with broker fee schedules (per-share commission, minimum per
  ticket, optional custody rate), chosen at registration. Fees deduct per
  execution in the fill path, deterministically, so replays stay exact.
- Bot parity: bots are player rows too (pre-approved, bank-assigned at roster
  build) and pay the same schedule — the leaderboard stays fair by construction.
- Modes are lanes, not a rewrite: today's one-shot daily play is casual with a
  daily reset; career (persistent purse) and pro (career + performance analytics)
  reuse the same tables behind a purse policy per round.

## Capabilities

### New Capabilities

- `player-identity`: registration, login, approval gate, password hashing and
  game-master password resets; socket participant binds to the identity.
- `brokerage`: banks, fee schedules, bank choice at registration, deterministic
  per-execution fee deduction.
- `administration`: the separate admin surface and realm, player approvals and
  resets, round control, future banks/fees management.

### Modified Capabilities

- `trading-game-session`: participants become registered identities (human or
  bot) with a bank; fair ranking holds net of identical fee schedules.

## Impact

- New tables + Flyway migrations (`players`, `banks`, `player_accounts`,
  `player_holdings`); `persist` module grows a player store beside the event log.
- Edge: auth on REST/WS (login check, token realms), `/admin` entry + API
  namespace, fee lookup at order submission; web gains a second (plain) entry.
- Core matching: fee deduction inside the fill path (deterministic; codec-safe).
- Launchpad gains register/login/pending states; terminal gains a portfolio tab
  showing holdings, P&L and fees paid (planned alongside, implemented separately).
