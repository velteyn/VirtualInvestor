-- Derivatives fee schedules per bank (futures-options): percentage of notional
-- plus a ticket minimum for futures/options fills. Backfills the seeded banks
-- with a 0.1% rate and their equity ticket minimum.
ALTER TABLE banks ADD COLUMN series_rate DOUBLE PRECISION NOT NULL DEFAULT 0.001
    CHECK (series_rate >= 0);
ALTER TABLE banks ADD COLUMN series_minimum DOUBLE PRECISION NOT NULL DEFAULT 0.0
    CHECK (series_minimum >= 0);

UPDATE banks SET series_minimum = ticket_minimum WHERE series_minimum = 0.0;
