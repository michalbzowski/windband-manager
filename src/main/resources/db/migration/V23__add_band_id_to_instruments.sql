-- V23: Add band_id to instruments for multi-tenant isolation
-- Instruments (tags) should be scoped to a band/team so users cannot see or reuse
-- tag definitions from another team.

ALTER TABLE IF EXISTS instruments ADD COLUMN IF NOT EXISTS band_id BIGINT;

-- Backfill existing instruments using the band of the members who reference them.
-- If an instrument is used by exactly one band, assign that band.
-- If it is shared or unused, leave it NULL for now; app code treats NULL as legacy/global.
UPDATE instruments i
SET band_id = src.band_id
FROM (
    SELECT mi.instrument_id, MIN(m.band_id) AS band_id
    FROM member_instruments mi
    JOIN members m ON m.id = mi.member_id
    GROUP BY mi.instrument_id
) src
WHERE i.id = src.instrument_id
  AND i.band_id IS NULL;

-- Add index and FK constraint
CREATE INDEX IF NOT EXISTS idx_instruments_band ON instruments(band_id);
ALTER TABLE instruments
    ADD CONSTRAINT fk_instruments_band
    FOREIGN KEY (band_id) REFERENCES bands(id);

-- Keep a band-scoped uniqueness constraint. Legacy NULL-band rows remain allowed.
CREATE UNIQUE INDEX IF NOT EXISTS ux_instruments_band_name
    ON instruments(band_id, name);
