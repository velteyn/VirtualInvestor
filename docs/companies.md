# Company profiles

Per-instrument identities for the terminal's Company tab: one profile per row
in `server/core/src/main/resources/instruments.csv`, shipped as the static
asset `web/src/assets/companies.json`. No server code reads them; the tab
renders them without any network calls. The future newsroom links here —
profiles are the stable "about" page, articles are events.

## Conventions

Every profile has a `description` of 3–5 sentences in one plain
financial-profile voice, plus fundamentals mirrored from the universe row
(sector, type, reference price, betas, volatility, shares).

Three consistency rules, checked in review:

1. **Fictional names only.** No real companies, products, or outlets. Generic
   words are fine ("launch windows" is release timing, not an OS).
2. **Sector/price agreement.** The story must fit the universe row: a $640
   stock with 1.5B shares out is an established giant, not a scrappy startup;
   a $64 energy name is a developer, not a supermajor. Indexes (`VNDX`) and
   pre-revenue names (`GNOM`) say what they are instead of faking earnings.
3. **Headline plausibility.** The news generator writes "{name} beats
   quarterly expectations" and "{name} jumps on upgrade" for every equity, so
   every equity profile must describe a business that can plausibly report
   earnings, take analyst ratings, and guide quarters. Name the two or three
   metrics that would move the stock.

## Adding a profile (the 22nd-ticker test)

1. Add the instrument row to `instruments.csv` first — the universe is the
   source of truth.
2. Copy this template into `companies.json`:

```json
{
  "ticker": "XXXX",
  "name": "Xxxx Inc",
  "sector": "<must match the universe row>",
  "type": "EQUITY",
  "referencePrice": 0.0,
  "baseVolatility": 0.0,
  "marketBeta": 0.0,
  "sectorBeta": 0.0,
  "sharesOutstanding": 0,
  "description": "3-5 sentences: what it sells, how it makes money, what constrains it, which metrics move the stock."
}
```

3. Verify mechanically: every asset ticker exists in the universe with matching
   sector and reference price (see the check in the `company-profiles` change).
4. Worked example — hypothetical water utility `AQUA` at $45.10, sector
   Utilities: "Aqua Municipal supplies piped water to two million households
   under regulated rate cases. Rate-base growth and approved returns decide its
   earnings, and drought restrictions cap volumes in dry years. Capital spending
   on mains replacement earns a fixed return, so the capex plan is the growth
   story. Investors own it for the dividend, which has risen for a decade." —
   fictional, fits a $45 utility, and gives earnings ("rate cases") plus
   metrics (rate base, volumes) for headlines to reference.
