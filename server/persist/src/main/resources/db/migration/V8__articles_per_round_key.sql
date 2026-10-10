-- Newsroom-narrator fix: news ids repeat every round (sched-PEAR fires in
-- every session), so a global primary key made each round overwrite the
-- first round's rows. The key is per-round; the wire upsert stays per-newsId
-- within one round's stream.

ALTER TABLE articles DROP CONSTRAINT articles_pkey;
ALTER TABLE articles ADD PRIMARY KEY (round_id, news_id);
