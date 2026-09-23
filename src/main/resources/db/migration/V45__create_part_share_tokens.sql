-- Migration V45: part_share_tokens (US-7.11).
--
-- One live share token per voice row: a random UUIDv4 used as the ONLY public path segment
-- of a share link (/public/parts/{token}). The previous URL scheme embedded sequential
-- band/composition/part ids and let anyone iterate foreign bands' documents; a 122-bit random
-- token cannot be guessed and carries no enumerable information. Revocation is a single
-- UPDATE (rotate the token) — the old value stops resolving instantly.
--
-- Part_id is UNIQUE: exactly one live credential per voice. The FK is ON DELETE CASCADE so
-- deleting a voice (or, transitively, a composition) removes its link — a stale token can
-- never outlive the thing it points at.

CREATE TABLE IF NOT EXISTS part_share_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token       UUID         NOT NULL,
    part_id     BIGINT       NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by  VARCHAR(255) DEFAULT NULL,

    CONSTRAINT uq_part_share_tokens_token   UNIQUE (token),
    CONSTRAINT uq_part_share_tokens_part    UNIQUE (part_id),
    CONSTRAINT fk_part_share_tokens_part
        FOREIGN KEY (part_id) REFERENCES composition_instruments (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_part_share_tokens_token
    ON part_share_tokens (token);

COMMENT ON TABLE part_share_tokens IS
    'US-7.11 opaque public share tokens — one per composition_instruments voice row.';

-- ── Backfill ────────────────────────────────────────────────────────────────────────────────
-- Every part that existed before US-7.11 keeps working: musicians may already hold the OLD
-- enumerable link (which stays valid until AC4 retires it), and the modal must be able to show
-- a token link for a row nobody "opens" first. gen_random_uuid() is provided by pgcrypto, which
-- ships pre-installed in the pgvector image Railway runs and is available to PG13+ core without
-- the extension on PG13; on PG12 the extension is required. Idempotent: only mints rows missing
-- a token today.
INSERT INTO part_share_tokens (token, part_id, created_at, created_by)
SELECT gen_random_uuid(), ci.id, CURRENT_TIMESTAMP, 'backfill-v45'
FROM composition_instruments ci
WHERE NOT EXISTS (SELECT 1 FROM part_share_tokens pst WHERE pst.part_id = ci.id);
