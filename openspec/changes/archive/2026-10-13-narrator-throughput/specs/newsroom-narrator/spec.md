# Delta — newsroom-narrator (change: narrator-throughput)

## ADDED Requirements

### Requirement: Stale releases shed

The desk SHALL skip narrating a release whose validity window closed before
its turn (comparing the round's current tick against release tick plus
duration), logging the skip. Validity-weighting keeps earnings and macro
stories (long windows) while shedding expired noise.

#### Scenario: Priced-in noise skipped

- **WHEN** a short-window release waits past its duration
- **THEN** no generation is spent on it and the skip is logged

### Requirement: Tighter articles

Generated articles SHALL target 2-4 sentences / 80 words (`n_predict: 100`),
halving generation cost against the old 160-token cap while reading complete.

#### Scenario: Capped output completes

- **WHEN** the model finishes inside the cap
- **THEN** the body ends on a sentence boundary, not mid-sentence
