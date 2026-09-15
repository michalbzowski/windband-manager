-- Migration V38: CREATE TABLE composition_instruments (corrected, idempotent).
--
-- WHY THIS FILE EXISTS (see the disabled V36__create_composition_instrument.sql):
-- The original US-1.3 link entity shipped as a broken V36. Its DDL used
-- comma-separated predicates inside a single CHECK( ... ):
--     page_from  INTEGER NOT NULL CHECK (page_from      >= 1),
--     page_to    INTEGER NOT NULL CHECK (page_to        >= 1, page_to >= page_from)
-- PostgreSQL rejects that with "syntax error at or near ','" (42601), so the app
-- failed to start on Railway. Flyway rolled back and stamped version 36 = FAILED in
-- flyway_schema_history, holding a stale checksum. Editing V36 in place would then
-- trip "Detected fatal change of a MIGRATION", and Flyway cannot re-run a failed
-- version migration anyway — so the corrected schema is applied HERE, idempotently.
--
-- Every statement below is safe to run whether composition_instruments exists or not,
-- whether V36 is recorded in flyway history as FAILED / APPLIED (old checksum), or absent:
--   * CREATE TABLE ... IF NOT EXISTS  → no-op if present, creates otherwise
--   * DROP/CREATE INDEX on a named index → converges to the right index
--   * COMMENT ON ... → idempotent metadata only
-- CHECK predicates use AND (correct across PostgreSQL and H2) — never commas.

CREATE TABLE IF NOT EXISTS composition_instruments (
    id BIGSERIAL PRIMARY KEY,
    composition_id BIGINT NOT NULL,
    instrument_id  BIGINT NOT NULL,
    instrument_role VARCHAR(100) NOT NULL,
    page_from      INTEGER NOT NULL CHECK (page_from >= 1),
    page_to        INTEGER NOT NULL CHECK (page_to >= 1 AND page_to >= page_from),
    file_ref       VARCHAR(300) DEFAULT NULL,
    source         VARCHAR(10) NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('AI', 'MANUAL', 'HYBRID')),
    confidence_score DOUBLE PRECISION NOT NULL CHECK (confidence_score BETWEEN 0.0 AND 1.0),
    verified_by    VARCHAR(255) DEFAULT NULL,
    verified_at    TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_composition_instruments_composition
        FOREIGN KEY (composition_id) REFERENCES compositions (id) ON DELETE CASCADE,
    CONSTRAINT fk_composition_instruments_instrument
        FOREIGN KEY (instrument_id)  REFERENCES instruments (id) ON DELETE RESTRICT
);

-- Case-insensitive uniqueness per (composition, role): "Flet 1"/"FLET 1" collide,
-- but "Flet 1"+"Flet 2" are two legal rows. UNIQUE INDEX (expression index — some
-- PostgreSQL builds reject an inline expression-based UNIQUE constraint in CREATE TABLE).
-- Deterministic: drop-then-create so a pre-existing index with this name is always
-- replaced with the canonical definition, regardless of what was there before.
DROP INDEX IF EXISTS uq_composition_instruments_role;
CREATE UNIQUE INDEX uq_composition_instruments_role
    ON composition_instruments (composition_id, lower(instrument_role));

-- Parts tab dominant read path: "all parts for this composition, ordered by page_from".
DROP INDEX IF EXISTS idx_composition_instruments_composition_page;
CREATE INDEX idx_composition_instruments_composition_page
    ON composition_instruments (composition_id, page_from ASC);

COMMENT ON TABLE composition_instruments IS
    'US-1.3: link entity between Composition and Instrument vocabulary, with role + page/file locator + confidence';
COMMENT ON COLUMN composition_instruments.source IS
    'AI = proposal from an AI strategy; MANUAL = librarian-set; HYBRID = librarian tweaked the AI suggestion (US-4.x)';
COMMENT ON COLUMN composition_instruments.confidence_score IS
    'Producer confidence 0.0..1.0. US-4.x gate: score < 0.7 forces verified_before_distribution=true.';
