# Tasks

## 1. Impact model in the core

- [x] 1.1 Add an `OrderFlowImpact` component to `FundamentalModel` with transient and permanent coefficients, a decay, and a liquidity scale from `freeFloat`; verify a unit test that a given net flow moves `F` in the flow direction and by less for a more liquid instrument.
- [x] 1.2 Implement the reversion term that pulls `F` toward a per-instrument reference; verify a test that after a transient burst `F` reverts toward the reference and a sustained flow leaves a permanent offset.
- [x] 1.3 Keep the reference path as the market/sector/diffusion drift so the reference itself still moves; verify the fundamental still responds to factor moves with impact disabled (coefficients zero).

## 2. Flow signal from the runtime

- [x] 2.1 Accumulate net aggressive flow per instrument per tick where fills are applied in `RunningRound`; verify a test that net buy flow is positive and net sell negative for a scripted set of fills.
- [x] 2.2 Apply the impact each tick before bots act, so bots see and react to the moved fundamental; verify a test that a large player order changes the fundamental the bots then quote around.
- [x] 2.3 Ensure market makers re-quote to the moved fundamental rather than a stale one; verify a test that the best quote tracks the fundamental after a large flow.

## 3. Determinism and safety

- [x] 3.1 Confirm no new randomness: add/extend a golden-master test that a fixed seed + flow scenario hashes identically on re-run.
- [x] 3.2 Add a bounded-drift guard test: over a long run with continuous flow and no reversion, `F` stays within a configured band around the reference; verify the reversion keeps it bounded.

## 4. Observability and calibration

- [x] 4.1 Expose the impact coefficients, reversion strength, and the current `fundamental` vs `reference` gap via accessors; verify a test that reads them.
- [x] 4.2 Extend the calibration harness to run a round with flow and report impact decay and reversion; record the results in `docs/calibration.md`.

## 5. Participant risk limits

- [x] 5.1 Add equity-relative order sizing with a cap to the bot runners; verify a test that a bot's order size falls when its equity falls and never exceeds the cap.
- [x] 5.2 Add a risk floor: a participant at or below the floor stops trading and is flattened; verify a test that a losing strategy halts and takes no further orders.
- [x] 5.3 Verify losses are bounded: run a full round where a rival plays a losing strategy and confirm its final equity is within a small multiple of its capital, not deeply negative.

## 6. Verification against the live market

- [x] 6.1 Run a bot-only round on the server with impact enabled and verify the market still moves, ranks reorder, and settlement completes; compare rival outcomes with the pre-change run.
- [ ] 6.2 Run a round where a client submits a large order over the wire and verify the traded price and the fundamental both move and partially persist.
