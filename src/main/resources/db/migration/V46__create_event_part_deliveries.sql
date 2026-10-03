-- V46__create_event_part_deliveries.sql — US-6.6 (Audit trail per delivery).
--
-- Append-only audit of "who got which part, when, with what outcome" for every
-- `EventPartDeliveryCommandService.deliverParts(eventId, bandId, actor)` run. One row is ONE
-- musician × ONE voice decision inside ONE send attempt; each re-send appends a fresh block of
-- rows (new sent_at) — that accumulation IS the history. Outcome values are the exact policy
-- buckets US-6.3 already reports honestly in the UI banner:
--   DELIVERED              — the e-mail left the transport with this part inside
--   SEND_FAILED            — the transport rejected the envelope; reason carries the error text
--   SKIPPED_NO_CONSENT     — V25 email_consent=false → refused silently, never sent (hard gate)
--   SKIPPED_NO_EMAIL       — no mailbox on file
--   REFUSED_NO_SCORE_FILE  — covering-file gate refused the part before minting/sending
--
-- Denormalised snapshots rather than member/part FKs: this is a HISTORY, not a live reference.
-- A member or a voice row may be deleted or renamed after the fact — the audit must still say
-- exactly who received what at that time, and deleting a musician must not cascade into the
-- record of what they were (or were not) sent. The single real relationship is the event: drop
-- the event and its delivery history goes with it (same lifecycle rule as US-7.11's tokens).
-- No UPDATE/DELETE path exists in the code — the entity exposes no mutators (append-only).

CREATE TABLE IF NOT EXISTS event_part_deliveries (
    id               BIGSERIAL PRIMARY KEY,
    event_id         BIGINT       NOT NULL REFERENCES band_events(id) ON DELETE CASCADE,

    delivered_to     VARCHAR(255) NOT NULL,  -- member display name at send time
    recipient_email  VARCHAR(255) DEFAULT NULL, -- may be null: the SKIPPED_NO_EMAIL bucket has none
    piece_title      VARCHAR(255) DEFAULT NULL,
    part_role        VARCHAR(120) DEFAULT NULL,
    page_from        INTEGER      DEFAULT NULL,
    page_to          INTEGER      DEFAULT NULL,

    channel          VARCHAR(16)  NOT NULL DEFAULT 'EMAIL',
    outcome          VARCHAR(32)  NOT NULL,
    reason           VARCHAR(512) DEFAULT NULL, -- SEND_FAILED error text / gate detail
    actor            VARCHAR(255) DEFAULT NULL, -- e-mail ("system") of whoever triggered the send
    sent_at          TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_part_deliveries_event_id ON event_part_deliveries(event_id, sent_at);

COMMENT ON TABLE event_part_deliveries IS
    'US-6.6 append-only audit — one row per musician × part decision in each parts-delivery run.';
