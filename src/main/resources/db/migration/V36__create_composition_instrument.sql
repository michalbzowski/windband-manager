-- Migration V36: composition_instruments — the link entity between a Composition and the
-- band's Instrument vocabulary for one particular role on that composition (US-1.3).
--
-- Business rules:
--  * Each row maps (composition, instrument) onto one role ("Flet 1", "Trąbka Bb", ...),
--    with pageFrom/pageTo (PDF mode) and/or fileRef (ZIP mode) as the locator.
--  * One mapping per (composition, lowercase role) — case-insensitive uniqueness enforced
--    via a UNIQUE INDEX on (composition_id, lower(instrument_role)); "Flet 1" / "FLET 1"
--    are the same row, but "Flet 1" + "Flet 2" are two rows.
--  * The (composition, instrument) pair MUST share a band — enforced in Java by
--    CompositionInstrument.forComposition() BEFORE save; this table has no extra band_id
--    column because the composition already carries band_id NOT NULL, and a part row can
--    not float free (FK RESTRICT on composition_id). The redundant denormalized band_id
--    is intentionally omitted here for V36 — see the US-1.3 plan which asks for it; any
--    future re-design should add it via a follow-up migration that backfills from
--    compositions.band_id, NOT by editing this one (Flyway: migrations are immutable).
--  * confidence_score is bounded [0.0, 1.0]; PartSource is one of AI / MANUAL / HYBRID
--    (stored as the enum name — see CompositionInstrumentIT).
--  * verified_by/verified_at form an audit pair frozen on first verify() call; the entity
--    enforces first-writer-wins so that subsequent re-verifications cannot hijack the pair.
--  * Cascade: composition removal deletes its parts (V36 FK ON DELETE CASCADE + the Java
--    @OneToMany(cascade=ALL, orphanRemoval) on Composition.parts — double net).

CREATE TABLE composition_instruments (
    id BIGSERIAL PRIMARY KEY,
    composition_id BIGINT NOT NULL,
    instrument_id  BIGINT NOT NULL,
    instrument_role VARCHAR(100) NOT NULL,
    page_from      INTEGER NOT NULL CHECK (page_from      >= 1),
    page_to        INTEGER NOT NULL CHECK (page_to        >= 1, page_to >= page_from),
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
        FOREIGN KEY (instrument_id)  REFERENCES instruments ( id) ON DELETE RESTRICT
);

-- Case-insensitive uniqueness per (composition, role): "Flet 1" / "FLET 1" collide,
-- but "Flet 1" + "Flet 2" are two legal rows. The same lower() trick as V32 — a
-- UNIQUE INDEX because some PostgreSQL builds reject expression-based UNIQUE
-- constraints inside CREATE TABLE (syntax error near "(").
CREATE UNIQUE INDEX uq_composition_instruments_role
    ON composition_instruments (composition_id, lower(instrument_role));

-- Parts tab (US-3.03) dominant read path: "all parts for this composition, page order".
CREATE INDEX idx_composition_instruments_composition_page
    ON composition_instruments (composition_id, page_from ASC);

-- NOTE on band-level scanning: row.band is not denormalised here — it is reached via
-- composition.band_id (NOT NULL) → bands.id. The US-5.x distribution preview joins
-- composition → bands; the index above (composition_id, page_from) already covers the
-- dominant path ("all parts in this composition, ordered by page"). A per-band scan
-- across *many* compositions is out of scope for US-1.3 (we have only ~20 compositions
-- on this schema to date); revisit when band-3 joins are a P95 hot spot.

COMMENT ON TABLE composition_instruments IS
    'US-1.3: link entity between Composition and Instrument vocabulary, with role + page/file locator + confidence';
COMMENT ON COLUMN composition_instruments.source IS
    'AI = proposal from an AI strategy; MANUAL = librarian-set; HYBRID = librarian tweaked the AI suggestion (US-4.x)';
COMMENT ON COLUMN composition_instruments.confidence_score IS
    'Producer confidence 0.0..1.0. US-4.x gate: score < 0.7 forces verified_before_distribution=true.';
