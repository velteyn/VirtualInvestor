# Tasks

## 1. Player persistence (persist)

- [x] 1.1 Add V3 migration with `players`, `banks` (seeded fee schedules), `player_accounts`, `player_holdings`; verify `mvn -o -pl persist test` migrates a scratch database cleanly.
- [x] 1.2 Implement the player store (register with unique username, PBKDF2 password hash, approval status, bank choice; purse load/save); verify round-trip persistence tests against the compose database.
- [x] 1.3 Seed one approved `BOT` row per roster name idempotently; verify re-running the seed changes nothing.

## 2. Identity on the wire (edge + web)

- [x] 2.1 Add `POST /api/players/register` and `/api/players/login` (versioned, duplicate/unknown-bank/wrong-password rejections); verify HTTP tests for each rejection plus the happy path.
- [x] 2.2 Bind the socket participant to the logged-in identity and refuse unapproved logins with `not-approved`; verify a WebSocket test that pending login cannot join and approved login routes orders to the player.
- [x] 2.3 Add launchpad register/login/pending screens; verify `npm run build` and a manual register-to-pending flow in the browser.

## 3. Admin backyard (edge + web)

- [x] 3.1 Gate `/api/admin/*` on admin tokens issued from the `VI_ADMIN_PASSWORD` env login; verify player tokens get 401 on admin routes and admin tokens get 401 on game routes.
- [x] 3.2 Implement approvals, password reset (invalidating sessions), and purse reset endpoints; verify each with API tests including the reset-login-with-new-password flow.
- [x] 3.3 Implement admin round list/pause/resume/settle-early; verify pause and settle-early against a live test round.
- [x] 3.4 Add the plain `/admin` web entry with login, approvals queue, player reset actions, and round controls; verify `npm run build` and a manual approve-then-login flow.

## 3b. Admin player CRUD and forced password change

- [x] 3.5 Backend: V4 `must_change_password` flag, admin create/edit/delete endpoints, public change-password endpoint verifying the old password; verify API tests for each including the forced-change login flow.
- [x] 3.6 Frontend: admin add/edit/delete controls and launchpad forced-change screen; verify `npm run build` plus a manual create-then-first-login flow.

## 4. Purse load and settle (runtime + persist)

- [x] 4.1 Load the purse into the round account at join and write it back at settlement under the casual daily-reset policy; verify a two-round test where the second round starts from starting capital.
- [x] 4.2 Surface cash, holdings, and fees-paid in the account snapshot/resync frames; verify the resync test shows the persisted purse.

## 5. Bank fees in the fill path (runtime + edge)

- [x] 5.1 Deduct `max(ticket minimum, per-share x quantity)` per fill per counterparty in the runtime fill wrapper; verify a fill-accounting test with a known schedule including the ticket minimum binding.
- [x] 5.2 Assign banks to bots deterministically at roster build; verify identical seeds produce identical fee totals.
- [x] 5.3 Re-run calibration and record the new bot economics (expect drift from fee-aware equities); verify the full server suite stays green.

## 6. Verification and docs

- [x] 6.1 End-to-end over a live server: register, approve via `/admin`, trade, confirm fee totals and the portfolio view; verify all steps succeed in order.
- [x] 6.2 Document identity, banks/fees, and admin operation in `docs/`; verify a fresh operator can register, approve, and trade from the docs alone.
