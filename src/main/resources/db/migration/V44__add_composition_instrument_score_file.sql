-- US-7.14 — explicit part→score-file binding.
-- Nullable: legacy rows keep NULL so their "which PDF covers my pages"
-- behaviour (largest file that contains [pageFrom..pageTo]) survives intact.
-- New rows set this to the user-chosen PDF id (from the "Dodaj głos" dialog).
-- The FK enforces referential integrity — deleting a score file refuses while
-- part mappings still point at it (ON DELETE RESTRICT — we never cascade-delete
-- a mapping because it carries confidence / verified-by audit information).

ALTER TABLE composition_instruments
    ADD COLUMN IF NOT EXISTS score_file_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_composition_instruments_score_file_id
    ON composition_instruments (score_file_id);

ALTER TABLE composition_instruments
    ADD CONSTRAINT fk_composition_instruments_score_file
        FOREIGN KEY (score_file_id) REFERENCES score_files (id)
        ON DELETE RESTRICT;
