# Tasks — News tab merge

Web-only, no server changes.

## 1. Single news tab

- [x] 1.1 Fold the article history load into `newswire.ts`, add the per-row
  full-story popup (bodies over the length threshold only), and remove the
  `newsroom.ts` tab mount, and verify headlines stream, history loads for a
  fresh mount, and the popup opens/closes (visual check + `npm test` where
  pure).
- [x] 1.2 Extract any pure row/popup helpers so `npm test` covers
  expand-eligibility and id-keyed history merge.

## 2. Integration

- [x] 2.1 Run `npm test`, `npm run build` green and `openspec validate
  news-tab-merge --type change` valid.

## Workflow follow-up

- Archive `terminal-sidebar-layout` first, then this one.
