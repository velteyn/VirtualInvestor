# AGENTS.md

Project-wide guidance for agents working in this repository.

## Framework versions and best practices

- This project uses **Spring Boot 4.1.x** (Spring Framework 7, Jackson 3). Spring Boot 4 is a
  major, modernizing release with a different module layout and APIs than Spring Boot 2/3.
- **Before implementing any Spring feature, verify the current official best practice for the
  exact version in use by searching the internet** (Spring Boot and Spring Framework reference
  docs, release notes, and migration guides for 4.x). Do not rely on remembered Spring Boot 3
  patterns: packages, test slices, and configuration changed.
- Examples of what already moved in Boot 4 (verify, do not assume this list is complete):
  - Web test slices: `AutoConfigureMockMvc` now lives in
    `org.springframework.boot.webmvc.test.autoconfigure` (artifact
    `spring-boot-starter-webmvc-test`), not
    `org.springframework.boot.test.autoconfigure.web.servlet`.
  - The default JSON stack is **Jackson 3** (`tools.jackson.databind.ObjectMapper`), not
    `com.fasterxml.jackson.databind`.
  - Spring MVC API versioning is configured under `spring.mvc.apiversion.*` and requires a
    strategy (here: the `X-API-Version` header).
- After verifying, implement to the verified pattern and cite the version in a code comment only
  when the choice is non-obvious.
- When a Spring API is unclear, inspect the resolved dependency jars in `~/.m2/repository`
  (`javap`/`unzip -l`) as a source of truth for the version actually on the classpath.

## Build and test

- Server: `cd server && mvn test` (per module: `mvn -pl core test`, `-pl persist -am test`,
  `-pl edge -am test`).
- Persistence tests require the compose database: `cd infra && docker compose up -d db`.
- Web: `cd web && npm run build`.

## Repository conventions

- Never commit unless explicitly asked — except at archive: completing an OpenSpec
  archive is standing authorization to commit, so commit without asking.
- Commit at the end of every archived feature: when an OpenSpec change is archived
  (tasks complete, specs synced, change moved to `openspec/changes/archive/`), commit its
  working-tree changes as one per-feature commit (`feat(scope): ...`, matching the existing
  history style) before starting the next change. Do not let finished features accumulate
  as uncommitted work, and do not bundle unrelated changes into one commit.
- The OpenSpec change `virtualinvestor-foundation` governs the build; update its task checkboxes
  only when behavior is implemented and verified.
