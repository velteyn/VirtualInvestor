# Contributing to VirtualInvestor

Thanks for your interest — bug reports, balance observations with numbers,
and small focused pull requests are all welcome. Please read the
[Code of Conduct](CODE_OF_CONDUCT.md) first; be kind and stay technical.

## Prerequisites

- Java 21 (JDK) and Maven 3.9+
- Node.js 20+ and npm
- Docker with the Compose plugin (PostgreSQL runs in Compose)

## Run the checks

Every change should arrive with these green (same commands CI would run):

```bash
cd server && mvn test          # full server suite (core → edge)
cd web && npm test             # terminal unit tests (Vitest)
cd web && npm run build        # typecheck + production build
```

Persistence tests need the compose database: `cd infra && docker compose up -d db`.
The live edge resolves `core`/`runtime`/`persist` from `~/.m2`, so after
touching those modules run `mvn -pl <modules> install -DskipTests` before
restarting edge, or it silently runs stale engine code.

## Pull requests

- Keep it small and focused: one behavior per PR, matching the existing
  commit style (`feat(scope): …`, `fix(scope): …`).
- Add or update tests for the behavior you change; update `docs/` when
  player-visible behavior changes (especially `docs/news.md`,
  `docs/bots.md`, `docs/calibration.md` for simulation tuning).
- New instrument, bot, or news behavior needs a spec home: check
  `openspec/specs/` first and extend the relevant capability rather than
  inventing a parallel one.
- Describe what you verified: commands run, and any live playtest
  (round length, what you traded, what you observed).

## Reporting issues

Use the issue templates (bug report / feature request) and include:
reproduction steps or the proposed behavior, expected vs actual, and the
versions involved (commit hash, Java/Node versions). For balance claims,
attach numbers — equity curves, fill logs, or calibration output beat
adjectives.

## Security

Do not open public issues for vulnerabilities — see [SECURITY.md](SECURITY.md).
