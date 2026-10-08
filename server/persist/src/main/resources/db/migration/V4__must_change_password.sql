-- Admin-set passwords force a change on first login (player-accounts 3.5).
-- Players created by the game master and password resets set the flag;
-- a successful change-password clears it.

ALTER TABLE players
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT false;
