-- US-2.3: ZIP Content Enumeration — add parent_file_id to score_files
-- Nullable: NULL = standalone upload (single PDF/ZIP), non-null = extracted from that ZIP row
-- Idempotent: safe under repeated replay (Flyway stamps version once).

ALTER TABLE score_files
    ADD COLUMN IF NOT EXISTS parent_file_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_score_files_parent_file_id
    ON score_files (parent_file_id);

-- Self-referencing FK: a child row points at its parent ZIP row.
-- On delete of the parent ZIP, cascade-delete all extracted entries.
ALTER TABLE score_files
    ADD CONSTRAINT fk_score_files_parent_file
        FOREIGN KEY (parent_file_id) REFERENCES score_files (id)
        ON DELETE CASCADE;
