# Spec Delta

## MODIFIED Requirements

### Requirement: Newsroom tab

The terminal SHALL offer the round's articles inside the News tab: every wire
row with a story body offers a full-story affordance that opens the complete
article text in place, and the tab's initial load serves the archived
articles so late joiners read the session's story so far. No separate
newsroom tab remains.

#### Scenario: Reading the session's story

- **WHEN** a player opens the News tab mid-round
- **THEN** every article so far is readable without leaving the terminal

#### Scenario: Full story expands in place

- **WHEN** a wire row with a story body is opened
- **THEN** the complete article text shows inline without navigating away,
  and closing it returns to the wire
