# administration Specification

## Purpose
The game-master backyard: a separate admin surface and credential realm for
approving players, recovering accounts, resetting purses, and controlling
rounds — unreachable with player credentials and invisible to the game itself.

## ADDED Requirements

### Requirement: Manual player creation

The system SHALL let the game master create a player with a username, an
initial password and a bank, active immediately without approval, and SHALL
force a password change on first login.

#### Scenario: Admin creates a player

- **WHEN** the game master creates a player with a username, password and bank
- **THEN** the player can log in at once and must choose a new password first

#### Scenario: Duplicate username refused

- **WHEN** the game master creates a player with a taken username
- **THEN** creation is refused and nothing changes

### Requirement: Player edit and delete

The system SHALL let the game master rename a player, reassign their bank
(effective on future fills only), and delete a player together with purse,
holdings and sessions.

#### Scenario: Bank reassignment applies going forward

- **WHEN** the game master moves a player to another bank
- **THEN** later fills use the new schedule while past fees stand

#### Scenario: Delete removes everything

- **WHEN** the game master deletes a player
- **THEN** login fails, sessions die, and no purse or holdings remain; working
  orders in a live round trade out under the orphaned id until it ends

### Requirement: Separate surface and credential realm

The system SHALL serve administration under a dedicated `/admin` surface
guarded by a master password supplied through the environment, and SHALL
reject player session tokens on admin calls and admin tokens on game calls.

#### Scenario: Player token refused on admin API

- **WHEN** an admin call presents a player session token
- **THEN** the call is rejected as unauthorized

#### Scenario: Admin token refused on game API

- **WHEN** a game call presents an admin token
- **THEN** the call is rejected as unauthorized

### Requirement: Player approvals

The system SHALL list pending registrations to the game master and SHALL move
a player to approved or rejected, unblocking or refusing their login
accordingly.

#### Scenario: Approve unlocks login

- **WHEN** the game master approves a pending player
- **THEN** that player's next login succeeds and binds their participant

#### Scenario: Reject blocks login

- **WHEN** the game master rejects a pending player
- **THEN** that player's login is refused as not approved

### Requirement: Password reset

The system SHALL let the game master set a new password for any player,
invalidating that player's existing sessions.

#### Scenario: Reset recovers a locked-out player

- **WHEN** the game master resets a player's password
- **THEN** login with the old password fails and login with the new one succeeds

### Requirement: Purse reset

The system SHALL let the game master restore any player to the configured
starting capital with all holdings flattened.

#### Scenario: Reset clears debts and positions

- **WHEN** the game master resets a player's purse
- **THEN** cash equals starting capital and no holdings remain

### Requirement: Round control

The system SHALL let the game master list live rounds and pause, resume, or
settle each one early.

#### Scenario: Settle a round early

- **WHEN** the game master settles a live round
- **THEN** the round closes, positions mark at the close, and ranking is produced
