# Tasks

## 1. Content asset

- [x] 1.1 Author `web/src/assets/companies.json` with one profile per universe instrument (description plus mirrored sector, reference price, betas, volatility, shares) and verify every ticker exists in `instruments.csv` with matching sector and reference price
- [x] 1.2 Review all 21 descriptions in one pass for fictional names only, sector/price consistency, and headline plausibility; verify no real company or outlet names appear

## 2. Company tab

- [x] 2.1 Build the company tab component (symbol selector defaulting to the chart symbol where cheap, profile + fundamentals display, terminal theme) following the portfolio-tab mounting pattern and verify `npm run build`
- [x] 2.2 Wire the tab into `mountTerminal` with `destroy()` lifecycle and verify on desktop and phone widths with a screenshot

## 3. Docs and verification

- [x] 3.1 Document profile conventions (fictional, sector/price-consistent, headline-plausible) in `docs/` and verify a fresh reader can write a consistent 22nd profile from the doc alone
- [x] 3.2 Run the full verification (`cd web && npm run build`, `openspec validate --specs`) and verify everything is green
