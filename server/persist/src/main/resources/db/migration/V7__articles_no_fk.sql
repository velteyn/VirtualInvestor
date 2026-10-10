-- Newsroom-narrator fix: production never writes the rounds table (it is
-- read-only recovery state), so the foreign key rejects every article.
-- Articles are keyed by round id as plain text and live and die with the
-- in-memory round; nothing deletes rounds rows, so no cascade is lost.

ALTER TABLE articles DROP CONSTRAINT articles_round_id_fkey;
