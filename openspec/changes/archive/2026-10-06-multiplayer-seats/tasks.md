# Tasks

## 1. Roster injection (runtime + core)

- [x] 1.1 Accept extra humans at round construction with equal funding; verify a runtime test where two humans trade as distinct participants.
- [x] 1.2 Move existing roster-shape fixtures to the injected model keeping solo games identical; verify the full runtime suite stays green.

## 2. Seats and purse fan-out (edge)

- [x] 2.1 Allocate `human`, `human-2`… in join order with first-N bot backfill and name-stable bank assignment; verify identical joins produce identical seat maps.
- [x] 2.2 Reset and write back purses per seat independently; verify a two-human round settles each purse to its own seat's account.
- [x] 2.3 Bind joins to the token's seat and refuse unseated tokens with `not-approved`; verify both rejections over the socket (the clock-based `started` lock moves to `game-lobby`).

## 3. Verification and docs

- [x] 3.1 Two humans share one round, trade (including against each other) and settle with per-seat purses; verify at service level (the two-socket live drill needs lobby room creation, so it moves to `game-lobby`).
- [x] 3.2 Document seats, backfill and the lock in `docs/`; verify the Ana/Bo/Carlo walkthrough reproduces as written.
