# Tasks

## 1. Waiting rooms (runtime + core)

- [ ] 1.1 Add `WAITING` to the lifecycle (CREATED→WAITING→WARMUP) keeping the solo path identical; verify lifecycle transition tests.
- [ ] 1.2 Support pre-start roster changes (append human, yield last rival) refusing once ticked; verify a test that joins then starts with the backfilled roster.

## 2. Rooms API (edge)

- [ ] 2.1 Add public live-games listing with state, preset, time, seats and leader; verify listings for waiting and live rounds.
- [ ] 2.2 Add room create (held world, creator master), join (seat claim, full/started refusals) and master start (creator-or-full, idempotent); verify each over HTTP including both refusals.
- [ ] 2.3 Expire unstarted rooms after the create-time TTL; verify an expired room vanishes without side effects.

## 3. Lobby screen (web)

- [ ] 3.1 Build the lobby after login: games table with join buttons, single-player start and room creation; verify `npm run build`.
- [ ] 3.2 Show waiting rooms with seats and master start for the master; verify a two-browser drill where the second player joins and the master starts.
- [ ] 3.3 Style the lobby in the terminal theme with tables for tabular data; verify on desktop and phone widths.

## 4. Verification and docs

- [ ] 4.1 End-to-end Ana/Bo/Carlo walkthrough over live servers: create, join, backfill, master start, shared trade, settle, locked late knock; verify every beat in order.
- [ ] 4.2 Document the lobby, rooms and the lock in `docs/`; verify the walkthrough reproduces as written.
