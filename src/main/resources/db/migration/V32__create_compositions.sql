-- Migration V32: compositions — the band's score library root aggregate.
-- Composition belongs to exactly one band; title is unique within that band
-- (case-insensitive). Indexes support the main list and search queries.
-- Statuses: DRAFT, READY (verified part map), ARCHIVED (soft hidden).
-- See docs/plans/2026-09-14-biblioteka-utworw-plan-implementacji.md, Task 1.01.
-- NOTE: case-insensitive uniqueness is expressed as a UNIQUE INDEX rather than an
-- inline table constraint; some PostgreSQL builds reject expression-based
-- UNIQUE constraints in CREATE TABLE (syntax error near "("). A unique index
-- is the portable equivalent and is also queryable by EXPLAIN plans identically.

CREATE TABLE compositions (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) DEFAULT NULL,
    composer VARCHAR(150) DEFAULT NULL,
    arranger VARCHAR(150) DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    band_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_compositions_band FOREIGN KEY (band_id) REFERENCES bands (id) ON DELETE RESTRICT,
    CONSTRAINT ck_compositions_status CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED'))
);

-- Case-insensitive title uniqueness within a band. Use lower() explicitly so the
-- index is usable for both exact and casefolded lookups; equivalent to a UNIQUE
-- constraint but works across all PostgreSQL versions without a CREATE FUNCTION
-- or expression-based table constraint.
CREATE UNIQUE INDEX uq_compositions_band_title ON compositions (band_id, lower(title));

CREATE INDEX idx_compositions_band_status ON compositions (band_id, status);
CREATE INDEX idx_compositions_updated_at ON compositions (updated_at DESC);

COMMENT ON TABLE compositions IS
    'Score library: catalogued musical compositions owned by a band (US-1.01..1.05).';
COMMENT ON COLUMN compositions.status IS
    'DRAFT = newly created, READY = part map verified and dispatchable, ARCHIVED = hidden from default lists.';
