# Tasks

## 1. Validity check

- [x] 1.1 Add the expiry clause to mailbox visibility (`tick <= release + durationTicks`, reusing the dormant field) and verify a unit test where a signal past its window never fires even with zero delay
- [x] 1.2 Verify a delay-exceeds-validity test: a slow-desk signal whose delay outlasts a short window stays silent while a long-window signal fires
- [x] 1.3 Verify determinism is preserved (same-seed replay green unmodified; expiry is a pure function of timeline + tick)

## 2. Generator rule and watch

- [x] 2.1 Set validity from scope × magnitude in generation and verify the watch shows fast-burn earnings expiring within days while a long shock stays tradeable
- [x] 2.2 Confirm desk activity explicitly (which news classes MERIDIAN now systematically misses) and recalibrate watch bounds only where justified; record moves with before/after numbers
- [x] 2.3 Re-baseline bot economics in `docs/calibration.md` and verify numbers match a fresh run

## 3. Newsroom and integration

- [x] 3.1 Surface "priced in" states for expired items wherever headlines render (news panel now, newsroom tab when it exists) and verify against a live session
- [x] 3.2 Run the full verification (`cd server && mvn test`, `cd web && npm run build`, `openspec validate --specs`) and verify everything is green
