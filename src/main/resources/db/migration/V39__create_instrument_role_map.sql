-- US-1.4: instrument_role_map — tag→role mapping per band (Trąbka → Trąbka 1 / Trąbka 2 / Kornet 1)
--
-- WHY V39 (not the doc's "V21"/"V22"):
--   The user-stories doc predates many later migrations; its suggested V21/V22 are long occupied.
--   V38 is occupied by US-1.3's corrected `composition_instruments` DDL (merged to main before this
--   branch was created), so the first free slot on this branch is V39 — matching "V36/V37 zepsute,
--   następ po V37" with one version bump for V38.
--
-- Every statement below is idempotent enough to survive a failed-then-replayed Flyway history:
--   * CREATE TABLE IF NOT EXISTS         → no-op if present
--   * DROP/CREATE INDEX on a named index → always converges to the canonical definition even if
--     a prior attempt left a differently-shaped index under the same name behind
--   * INSERT ... WHERE NOT EXISTS(...)   → only seeds rows that are not already present

CREATE TABLE IF NOT EXISTS instrument_role_map (
    id                  BIGSERIAL      PRIMARY KEY,
    band_id             BIGINT         NOT NULL REFERENCES bands (id) ON DELETE CASCADE,
    source_tag          VARCHAR(60)    NOT NULL,
    target_role_pattern VARCHAR(100)   NOT NULL,
    description         VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_instrument_role_map_source_tag_not_blank
        CHECK (char_length(trim(source_tag)) > 0),
    CONSTRAINT chk_instrument_role_map_target_role_pattern_not_blank
        CHECK (char_length(trim(target_role_pattern)) > 0)
);

-- Case-insensitive uniqueness per (band, lower(source_tag), target_role_pattern):
-- "Trąbka + Trąbka 1" and "trąbka + Trąbka 1" are ONE logical row; "Trąbka → Trąbka 2" is legal.
-- Deterministic drop-then-create mirrors US-1.3 (V38) so a prior partial attempt cannot leave a
-- stale shape under the same canonical name.
DROP INDEX IF EXISTS uq_instrument_role_map;
CREATE UNIQUE INDEX uq_instrument_role_map
    ON instrument_role_map (band_id, lower(source_tag), target_role_pattern);

-- Dominant read path (US-1.5 distributor: "every role for this tag" + admin UI list by band).
DROP INDEX IF EXISTS idx_instrument_role_map_band_source_tag_lower;
CREATE INDEX idx_instrument_role_map_band_source_tag_lower
    ON instrument_role_map (band_id, lower(source_tag));

COMMENT ON TABLE instrument_role_map IS
    'US-1.4: per-band mapping from member instrument tag to composition role label';
COMMENT ON COLUMN instrument_role_map.source_tag IS
    'Member-facing free-form instrument tag ("Trąbka", "Flet", …); case-insensitive when compared';
COMMENT ON COLUMN instrument_role_map.target_role_pattern IS
    'Composition instrument role this tag maps to (free-form, e.g. "Trąbka 1", "Kornet Bb")';

-- ------------------------------------------------------------------
-- Seed: default mappings for the "default" band (band_id = 1, per spec).
--
-- A CTE (`seed`) holds every row exactly once and a single
-- `INSERT ... SELECT s.* FROM seed s WHERE NOT EXISTS(...)` writes them
-- all in one statement. This avoids six near-duplicate INSERT blocks
-- (and the typos they tend to collect), while staying idempotent under
-- repeated replay: `NOT EXISTS` skips rows that are already present, so
-- a re-run touches zero rows (Flyway `repair` / rollback+forward safe).
-- ------------------------------------------------------------------
WITH seed (source_tag, target_role_pattern, description) AS (
    VALUES
        ('Trąbka',    'Trąbka 1',    'Trumpet / cornet in B-flat — first seat role'),
        ('Trąbka',    'Trąbka 2',    'Trumpet / cornet in B-flat — second seat role'),
        ('Flet',      'Flet 1',      'Flute (concert C or G) — first seat role'),
        ('Waltornia', 'Waltornia 1', 'French horn — first seat role'),
        ('Puzon',     'Puzon 1',     'Trombone — first seat role'),
        ('Saksofon',  'Saksofon 1',  'Alto saxophone — first seat role')
)
INSERT INTO instrument_role_map (band_id, source_tag, target_role_pattern, description, created_at, updated_at)
SELECT
    1,
    s.source_tag,
    s.target_role_pattern,
    s.description,
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC',
    CURRENT_TIMESTAMP AT TIME ZONE 'UTC'
FROM seed s
WHERE NOT EXISTS (
    SELECT 1 FROM instrument_role_map m
     WHERE m.band_id             = 1
       AND lower(m.source_tag)   = lower(s.source_tag)
       AND m.target_role_pattern = s.target_role_pattern
);
