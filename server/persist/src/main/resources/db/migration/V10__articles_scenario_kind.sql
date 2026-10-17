-- Narrator-arcs: scenario beats persist under their own kind so the
-- newsroom selects them and the wire badges them as stories, not earnings.
-- Existing rows are untouched; the constraint merely gains a value.
ALTER TABLE articles DROP CONSTRAINT IF EXISTS articles_kind_check;
ALTER TABLE articles ADD CONSTRAINT articles_kind_check
    CHECK (kind IN ('EARNINGS', 'SECTOR', 'MACRO', 'PREVIEW', 'RECAP', 'TEMPLATE', 'SCENARIO'));
