-- Newsroom articles (newsroom-narrator 1.2): generated story bodies for news
-- releases, keyed by news id so live payloads and the history endpoint agree.
-- Articles die with their round, like every other round-scoped read model.

CREATE TABLE articles (
    news_id            TEXT PRIMARY KEY,
    round_id           TEXT NOT NULL REFERENCES rounds (round_id) ON DELETE CASCADE,
    tick               BIGINT NOT NULL,
    kind               TEXT NOT NULL CHECK (kind IN ('EARNINGS', 'SECTOR', 'MACRO', 'PREVIEW', 'RECAP', 'TEMPLATE')),
    tickers            TEXT NOT NULL DEFAULT '',
    headline           TEXT NOT NULL,
    body               TEXT NOT NULL,
    model              TEXT NOT NULL DEFAULT 'template',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX articles_round_idx ON articles (round_id, tick);
