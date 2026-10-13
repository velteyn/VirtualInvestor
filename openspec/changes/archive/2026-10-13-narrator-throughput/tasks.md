# Tasks — Narrator throughput

## 1. Cheaper generations

- [x] 1.1 `n_predict` 100 + 80-word prompt; unit tests (prompt text,
  captured payload cap).

## 2. Shed stale work + observability

- [x] 2.1 Tick tracking, staleness shed, per-article elapsed/outcome logging;
  `isStale` unit tests.

## 3. Integration

- [x] 3.1 Narrator + edge suites green, `openspec validate` green; live
  round shows faster articles with skip/publish log lines.
