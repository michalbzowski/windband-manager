-- US-7.15 — score-file content replacement ("Wymień plik").
-- A replaced row KEEPS its id (part mappings in composition_instruments.score_file_id
-- stay valid — that is the whole point), so the content swap is recorded as metadata:
-- replaced_at stamps the moment the bytes behind the row changed. NULL = never replaced.
ALTER TABLE score_files
    ADD COLUMN IF NOT EXISTS replaced_at TIMESTAMP;
