-- The event/snapshot payloads are encoded by the dependency-free RecordTextCodec, which is
-- not JSON. Store them as TEXT rather than JSONB so the codec output round-trips verbatim.

ALTER TABLE events ALTER COLUMN payload TYPE TEXT USING payload::text;
ALTER TABLE snapshots ALTER COLUMN state TYPE TEXT USING state::text;
