# Spec Delta

## MODIFIED Requirements

### Requirement: Event-driven articles

The system SHALL generate one article per selected news release, plus a round preview at start and a recap at end, without delaying or altering the market. For staged stories the article SHALL use the stage's authored lede (rumor, confirm, or deny) as its headline and opening, so template and sidecar prose continue the same beat.

#### Scenario: Earnings become stories

- **WHEN** a company earnings release fires during a round
- **THEN** an article appears on the wire carrying the headline, the company profile's context, and the release's magnitude — while the tick engine and bot signals proceed exactly as without it

#### Scenario: The market never waits for prose

- **WHEN** the sidecar is slow or unreachable
- **THEN** the headline still publishes immediately, and the article follows when ready — or a template article stands in, or nothing does

#### Scenario: Confirmation continues the story

- **WHEN** a staged rumor resolves
- **THEN** the resolution article carries the confirm-lede or deny-lede and references the rumor outcome instead of restating the rumor

## ADDED Requirements

### Requirement: Scope template packs for articles

The narrator SHALL render template-fallback bodies from the same per-scope packs as the wire (takeover, regulatory, guru, analyst-color, taunt), filling subject slots from release subjects and capping bodies at 200 chars, so template prose and wire ledes never disagree.

#### Scenario: Template fallback matches the wire

- **WHEN** the sidecar is down and a company story fires
- **THEN** the template body uses the company pack's stage template with the same subjects as the wire lede
