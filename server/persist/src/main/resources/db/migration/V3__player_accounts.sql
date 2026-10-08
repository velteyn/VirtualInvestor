-- Player identity, banks and the persistent purse (player-accounts 1.1).
-- Deliberately independent of rounds/participants/accounts: the V1 round tables
-- cascade-delete with their round, while the purse must survive it.

CREATE TABLE banks (
    bank_id            TEXT PRIMARY KEY,
    display_name       TEXT NOT NULL,
    fee_per_share      DOUBLE PRECISION NOT NULL CHECK (fee_per_share >= 0),
    ticket_minimum     DOUBLE PRECISION NOT NULL CHECK (ticket_minimum >= 0),
    custody_rate       DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (custody_rate >= 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE players (
    player_id          TEXT PRIMARY KEY,
    username           TEXT NOT NULL UNIQUE,
    password_hash      TEXT NOT NULL,
    password_salt      TEXT NOT NULL,
    kind               TEXT NOT NULL CHECK (kind IN ('HUMAN', 'BOT')),
    status             TEXT NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    bank_id            TEXT NOT NULL REFERENCES banks (bank_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE player_accounts (
    player_id          TEXT PRIMARY KEY REFERENCES players (player_id) ON DELETE CASCADE,
    cash               DOUBLE PRECISION NOT NULL,
    fees_paid          DOUBLE PRECISION NOT NULL DEFAULT 0,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE player_holdings (
    player_id          TEXT NOT NULL REFERENCES players (player_id) ON DELETE CASCADE,
    symbol             TEXT NOT NULL,
    qty                BIGINT NOT NULL,
    avg_price          DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (player_id, symbol)
);

-- Seed fee schedules: discount, standard and premium price points so bank choice
-- is a meaningful difficulty setting. Idempotent on bank_id.
INSERT INTO banks (bank_id, display_name, fee_per_share, ticket_minimum, custody_rate) VALUES
    ('first-ledger', 'First Ledger Bank', 0.005, 1.00, 0.0),
    ('meridian', 'Meridian Trust', 0.01, 2.00, 0.0025),
    ('apex', 'Apex Private', 0.02, 5.00, 0.005)
ON CONFLICT (bank_id) DO NOTHING;
