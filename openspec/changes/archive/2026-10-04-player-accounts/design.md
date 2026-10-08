# Design

## Context

See `proposal.md` for motivation. Current state: every socket plays as a hardcoded
`human` (`GameWebSocketHandler` binds `participantId` per session, so identity has a
slot waiting); `SessionRegistry`/`SessionToken` already establish the hash-compare
token pattern, but in memory and per round. `Account.applyFill` is the single choke
point for every execution. V1 schema tables (`participants`, `accounts`,
`positions`) are per-round read models that cascade-delete with the round — the
purse must live elsewhere. No Spring Security dependency is on the classpath.

## Goals / Non-Goals

**Goals:** registered identities for humans and bots, approval-gated login, bank
choice with deterministic per-execution fees, a separated admin backyard, and a
purse that survives the round.

**Non-Goals:** email (verification, recovery, notifications); self-service password
recovery; custody billing (the schedule column exists, billing lands later);
per-symbol realized P&L (portfolio-tab change, separate); multi-human same-round
matchmaking UI (bindings already allow it; not built here).

## Decisions

### D1. Password hashing without a new dependency

**Decision:** PBKDF2-HMAC-SHA256 via `javax.crypto` (JDK built-in), per-user salt,
high iteration count; verify with constant-time comparison mirroring the existing
`SessionToken.matches` style.

**Rationale:** No Spring Security on the classpath and this machine builds offline;
a new Maven dependency risks the build for zero gain — the JDK primitive is the
standard choice for password storage.

**Alternatives:** spring-security-crypto BCrypt (nicer API, needs the artifact in
`~/.m2` first — revisit if Spring Security arrives for another reason).

### D2. Fees in the runtime fill wrapper, not in core `Account`

**Decision:** `RunningRound.applyFill` (which already touches both counterparties
per fill) looks up each participant's bank schedule and debits
`max(ticket minimum, per-share x quantity)` in cash right after the fill posts.
Core `Account` stays venue-neutral and untouched.

**Rationale:** Determinism is preserved (pure function of the fill, same tick, same
order in the event stream) with minimum blast radius: no core economics change,
no `FillAccountingTest` churn. Maker and taker each pay their own bank's schedule
through the same code path, which is exactly the bot-parity requirement.

**Alternatives:** fee parameter on `Account.applyFill` (touches core + fixtures for
no behavioral gain).

### D3. Purse tables separate from the round read model

**Decision:** New migration V3 adds `players` (id, username unique, password hash,
kind `HUMAN`/`BOT`, status `PENDING`/`APPROVED`/`REJECTED`, bank reference),
`banks` (seeded fee schedules), `player_accounts` (cash, fees paid), and
`player_holdings` (symbol, qty, avg) — none referencing `rounds`, so purse data
survives round deletion. Round start loads the purse into the in-memory
`Account`; settlement writes it back. The existing per-round tables are untouched.

**Rationale:** The V1 tables cascade-delete with their round by design; hanging
persistent wealth off them would silently wipe careers.

### D4. Backyard shares the process, nothing else

**Decision:** Same edge process serves a second Vite entry at `/admin/*` and
`POST /api/admin/*` guarded by an interceptor that accepts only admin tokens.
Master password arrives via environment (`VI_ADMIN_PASSWORD`); admin tokens reuse
the `SessionToken` hash pattern in a separate in-memory registry. Player tokens
are rejected on admin routes and admin tokens on game routes, both with 401.

**Rationale:** One deployment, zero shared auth surface; splitting the process
later changes packaging, not the contract.

### D5. Bots are seeded players, bank-assigned by roster index

**Decision:** Round setup idempotently ensures one approved `BOT` row per roster
name and assigns banks deterministically (roster index mod bank count), so bot
economics are identical run to run.

**Rationale:** Parity falls out of the data model instead of living in branching
code; determinism (and the golden-master rerun-equality) holds because nothing
about assignment depends on wall state.

### D6. Casual purse policy now, career flag later

**Decision:** Round creation takes the launchpad capital as today and resets the
purse to it (daily one-shot); the write-back path is built, and a later change
adds a `career` policy that loads the stored purse instead. No behavior forks in
this change.

## Risks / Trade-offs

- **Fees shift bot economics** (equity-gated risk floor sees net wealth) → absolute
  simulation values move; rerun-equality still holds and no golden values are
  checked in, but expect calibration numbers (LEDGER +12%) to drift on first run.
- **Admin token theft** → v1 tokens live in memory and die on restart (acceptable
  for a backyard on a tailnet; expiry is a later hardening item, like player tokens).
- **Pending-state UX** → launchpad needs register/login/pending screens; an
  unapproved socket gets explicit `not-approved`, never a silent hang.
- **Two Vite entries** → terminal bundle and admin bundle diverge; admin stays
  deliberately plain to keep this cheap.

## Migration Plan

Additive V3 migration (new tables + bank seeds); rollback is dropping them —
existing tables and the event log are untouched. Deploy order: migrate, restart
edge, register the first human, approve via admin, trade. Bots self-seed on the
next round creation.

## Open Questions

- Admin UI polish level (plain forms suffice for v1 — confirm).
- Token expiry policy for players and admins (deferred; current tokens don't expire).
