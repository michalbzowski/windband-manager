-- Migration V35: score_files — uploaded score file metadata (Task 1.06).
-- Each row describes ONE binary stored on disk belonging to exactly one
-- composition (PDF or ZIP). The write path (upload adapter + validation) lands
-- in Task 1.07; the download endpoint and cascade-deletion land in 1.08/1.09.
-- See docs/plans/2026-09-14-biblioteka-utworw-plan-implementacji.md, Task 1.06.
-- Conventions follow V32 (compositions): FK RESTRICT is intentionally avoided;
-- the application deletes files first (Task 1.09) before removing the parent
-- composition, so RESTRICT would only guard against a future regression rather
-- than enable any flow — keep ON DELETE CASCADE to match the band-scoped tree.

CREATE TABLE score_files (
    id BIGSERIAL PRIMARY KEY,
    composition_id BIGINT NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    sha256 VARCHAR(64) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    original_name VARCHAR(300) DEFAULT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_score_files_composition FOREIGN KEY (composition_id)
        REFERENCES compositions (id) ON DELETE CASCADE
);

-- Latest-per-composition is the dominant access pattern (download endpoint).
CREATE INDEX idx_score_files_composition_created
    ON score_files (composition_id, created_at DESC);

-- Integrity + future dedup hook (same band, same hash) — plain index; a UNIQUE
-- constraint would forbid re-uploads of the same title/revision pair, which is
-- a product decision deferred to Task 1.07+.
CREATE INDEX idx_score_files_sha256 ON score_files (sha256);

COMMENT ON TABLE score_files IS
    'Uploaded score file (PDF or ZIP) metadata per composition; binary lives on disk (US-2.xx).';
COMMENT ON COLUMN score_files.mime_type IS
    'Stored MIME — application/pdf or application/zip (validated by magic bytes in Task 1.07).';
COMMENT ON COLUMN score_files.size_bytes IS
    'Content length at upload time; capped upstream by windband.scores.maxPdfBytes/maxZipBytes.';
COMMENT ON COLUMN score_files.sha256 IS
    'Hex SHA-256 of the stored binary — integrity check + dedup hook.';
COMMENT ON COLUMN score_files.storage_path IS
    'Absolute path inside the configured scores root (windband.scores.rootDir).';
