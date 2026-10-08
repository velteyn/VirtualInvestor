# player-identity Specification

## Purpose
Who plays: human registration and login without email, an approval gate before
play, bot identities on the same footing, and socket sessions bound to the
identity so a player returning later is the same participant.

## ADDED Requirements

### Requirement: Mandatory password change

The system SHALL flag logins that use an admin-set password and SHALL require
the player to choose a new password (via a dedicated endpoint verifying the old
one) before playing; admin password resets set the same flag.

#### Scenario: First login forces a change

- **WHEN** a player logs in with an admin-set password
- **THEN** the login reports change-required and the terminal holds the session
  config until the password is changed

#### Scenario: Change verifies the old password

- **WHEN** a password change presents a wrong old password
- **THEN** it is refused and the flag stays set

### Requirement: Username and password registration

The system SHALL register a player from a unique username, a password, and a
chosen bank, with no email address required, and SHALL store only a salted hash
of the password, never plaintext.

#### Scenario: New registration pends approval

- **WHEN** a new username registers with a password and a bank
- **THEN** a pending player record is created and the player is told approval is required before play

#### Scenario: Duplicate username refused

- **WHEN** a username that already exists registers again
- **THEN** registration is refused with a taken-username error and no record changes

#### Scenario: No plaintext passwords

- **WHEN** an operator inspects the player store
- **THEN** no password is recoverable in readable form

### Requirement: Approval gate on login

The system SHALL refuse login for pending or rejected players with an explicit
not-approved reason, and SHALL bind an approved login's socket participant to
that player identity for the session.

#### Scenario: Pending player cannot play

- **WHEN** a pending player logs in
- **THEN** login is refused as awaiting approval and no round can be joined

#### Scenario: Approved login binds the participant

- **WHEN** an approved player logs in and joins a round
- **THEN** all of that socket's orders, positions and P&L accrue to the player identity

### Requirement: Reconnect restores identity

The system SHALL restore the same player identity when a disconnected socket
reconnects with its session token, preserving orders, positions and account.

#### Scenario: Reconnect keeps the participant

- **WHEN** a player drops and reconnects with a valid session token
- **THEN** the resynchronized state belongs to the same player, not a new one

#### Scenario: Foreign token rejected

- **WHEN** a socket presents another player's session token
- **THEN** the bind is refused and nothing of that player is exposed
