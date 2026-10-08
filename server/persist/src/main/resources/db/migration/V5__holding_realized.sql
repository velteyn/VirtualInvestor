-- Per-symbol realized P&L on stored holdings (portfolio-tab 1.2). Closed legs
-- keep their booked gain in the purse even after the position flattens.

ALTER TABLE player_holdings
    ADD COLUMN realized DOUBLE PRECISION NOT NULL DEFAULT 0;
