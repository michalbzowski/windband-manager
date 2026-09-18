-- US-4.1: score_analysis — one AI-analysis run per (composition + score file) pair.
--
-- State machine: PENDING -> RUNNING -> {SUCCEEDED | FAILED}  (see the ScoreAnalysis entity
-- for the full invariant set; the columns below are the exact shape it persists).
--
-- WHY V42: V41 was already occupied by US-1.1's `add_scorefile_parent_file_id`
--   (merged to main before this branch); the first free slot on this branch is V42.
--
-- Idempotent under Flyway replay: CREATE TABLE IF NOT EXISTS, DROP/CREATE INDEX named,
-- INSERT ... WHERE NOT EXISTS (not used here — seed data is added by a separate V43 if needed).

CREATE TABLE IF NOT EXISTS score_analysis (
    id                          BIGSERIAL              PRIMARY KEY,
    composition_id              BIGINT                 NOT NULL REFERENCES compositions (id) ON DELETE CASCADE,
    score_file_id               BIGINT                 NOT NULL,
    phase                       VARCHAR(16)            NOT NULL DEFAULT 'PENDING',
    runner_ref                  VARCHAR(255),
    error_message               TEXT,                    -- set only on phase='FAILED'; nullable otherwise
    arrangement_json_path       VARCHAR(1024),
    arrangement_musicxml_path   VARCHAR(1024),
    arrangement_mid_path        VARCHAR(1024),
    validation_txt_path         VARCHAR(1024),
    started_at                  TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at                 TIMESTAMP WITH TIME ZONE,
    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_score_analysis_phase_valid
        CHECK (phase IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
    -- terminal rows must always have finished_at
    CONSTRAINT chk_score_analysis_terminal_finished
        CHECK (phase NOT IN ('SUCCEEDED','FAILED') OR finished_at IS NOT NULL),
    -- SUCCEEDED requires all four artefact paths (the pipeline contract)
    CONSTRAINT chk_score_analysis_succeeded_artefacts
        CHECK (phase <> 'SUCCEEDED'
               OR (arrangement_json_path     IS NOT NULL
                   AND arrangement_musicxml_path IS NOT NULL
                   AND arrangement_mid_path  IS NOT NULL
                   AND validation_txt_path   IS NOT NULL)),
    -- FAILED requires an error message
    CONSTRAINT chk_score_analysis_failed_error
        CHECK (phase <> 'FAILED' OR char_length(trim(error_message)) > 0)
);

-- Dominant read path: latest analysis per composition ("GET /analysis/latest").
DROP INDEX IF EXISTS idx_score_analysis_composition_id_created_at;
CREATE INDEX idx_score_analysis_composition_id_created_at
    ON score_analysis (composition_id, created_at DESC);

-- Cheap polling for "is anything still running" across bands (admin dashboard, later epic).
DROP INDEX IF EXISTS idx_score_analysis_status;
CREATE INDEX idx_score_analysis_status
    ON score_analysis (phase);

COMMENT ON TABLE score_analysis IS
    'US-4.1: one AI-analysis run per (composition + score file) pair — PENDING → RUNNING → SUCCEEDED | FAILED';
COMMENT ON COLUMN score_analysis.score_file_id IS
    'The score file (PDF or ZIP of PDFs) that triggered this analysis row; nullable on composition, NOT on band (band isolation via composition.id)';
COMMENT ON COLUMN score_analysis.runner_ref IS
    'Process/handle identifier from the AiAnalysisRunner — null while PENDING, set to a PID/job id once RUNNING';
COMMENT ON COLUMN score_analysis.error_message IS
    'Polish user-visible failure reason; required when phase = FAILED (enforced by chk_score_analysis_failed_error)';
