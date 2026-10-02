-- VirtualInvestor persistence schema (task 9.1).
-- The event log plus snapshots are the source of truth; the other tables are read models.

CREATE TABLE rounds (
    round_id         TEXT PRIMARY KEY,
    seed             BIGINT NOT NULL,
    starting_capital DOUBLE PRECISION NOT NULL,
    duration_minutes BIGINT NOT NULL,
    preset           TEXT NOT NULL,
    state            TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE participants (
    participant_id   TEXT PRIMARY KEY,
    round_id         TEXT NOT NULL REFERENCES rounds (round_id) ON DELETE CASCADE,
    display_name     TEXT NOT NULL,
    type             TEXT NOT NULL,
    starting_capital DOUBLE PRECISION NOT NULL
);

CREATE TABLE accounts (
    participant_id TEXT PRIMARY KEY REFERENCES participants (participant_id) ON DELETE CASCADE,
    cash           DOUBLE PRECISION NOT NULL,
    realized_pnl   DOUBLE PRECISION NOT NULL DEFAULT 0
);

CREATE TABLE positions (
    participant_id TEXT NOT NULL REFERENCES participants (participant_id) ON DELETE CASCADE,
    symbol         TEXT NOT NULL,
    net_qty        BIGINT NOT NULL,
    avg_price      DOUBLE PRECISION NOT NULL,
    PRIMARY KEY (participant_id, symbol)
);

CREATE TABLE orders (
    order_id      TEXT PRIMARY KEY,
    round_id      TEXT NOT NULL,
    participant_id TEXT NOT NULL,
    symbol        TEXT NOT NULL,
    side          TEXT NOT NULL,
    type          TEXT NOT NULL,
    quantity      BIGINT NOT NULL,
    price         DOUBLE PRECISION NOT NULL,
    trigger_price DOUBLE PRECISION NOT NULL DEFAULT 0,
    status        TEXT NOT NULL,
    filled_qty    BIGINT NOT NULL DEFAULT 0,
    created_tick  BIGINT NOT NULL
);

CREATE TABLE fills (
    fill_id       BIGSERIAL PRIMARY KEY,
    round_id      TEXT NOT NULL,
    symbol        TEXT NOT NULL,
    maker_order_id TEXT,
    taker_order_id TEXT,
    price         DOUBLE PRECISION NOT NULL,
    quantity      BIGINT NOT NULL,
    tick          BIGINT NOT NULL
);

-- Append-only event log (task 9.2). Never updated or deleted.
CREATE TABLE events (
    round_id TEXT NOT NULL,
    seq      BIGINT NOT NULL,
    tick     BIGINT NOT NULL,
    type     TEXT NOT NULL,
    payload  JSONB NOT NULL,
    PRIMARY KEY (round_id, seq)
);

-- Periodic world-state snapshots (task 9.3).
CREATE TABLE snapshots (
    round_id   TEXT NOT NULL,
    tick       BIGINT NOT NULL,
    seq        BIGINT NOT NULL,
    state      JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (round_id, tick)
);

CREATE TABLE results (
    round_id        TEXT NOT NULL REFERENCES rounds (round_id) ON DELETE CASCADE,
    participant_id  TEXT NOT NULL,
    final_equity    DOUBLE PRECISION NOT NULL,
    return_fraction DOUBLE PRECISION NOT NULL,
    rank            INTEGER NOT NULL,
    PRIMARY KEY (round_id, participant_id)
);

CREATE INDEX idx_events_round_tick ON events (round_id, tick);
CREATE INDEX idx_snapshots_round_seq ON snapshots (round_id, seq);
