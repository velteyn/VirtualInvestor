# Delta — newsroom-narrator (change: playtest-fixes)

## ADDED Requirements

### Requirement: No invented names

Narration prompts without company subjects (previews, macro) SHALL instruct
the model to name no companies at all, so the sidecar cannot invent firms to
fill the void. Company news keeps the named-subjects rule.

#### Scenario: Preview names nothing

- **WHEN** a round preview is narrated with no subjects
- **THEN** the prompt carries the name-no-companies rule and the body stands
  without firm names
