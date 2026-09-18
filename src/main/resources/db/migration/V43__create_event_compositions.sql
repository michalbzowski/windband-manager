-- V43__create_event_compositions.sql — US-7.2 (Link Compositions to Events)
-- Junction table between band_events and compositions, with a 1-based setlist position
-- so "send all parts in concert order" works later. The same composition may appear on
-- multiple events; the same event may have many compositions. (No FK to `band` here —
-- cross-band writes are refused at the EventCommandService.assignComposition guard.)
CREATE TABLE IF NOT EXISTS event_compositions (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL REFERENCES band_events(id) ON DELETE CASCADE,
    composition_id  BIGINT NOT NULL REFERENCES compositions(id) ON DELETE CASCADE,
    order_in_set    INT    NOT NULL CHECK (order_in_set >= 1),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_event_compositions
        UNIQUE (event_id, composition_id)
);

CREATE INDEX IF NOT EXISTS idx_event_compositions_event_id ON event_compositions(event_id);

-- NOTE: Flyway V43 chosen because V42 (score_analysis_table) is the last merged migration
-- on this branch — see V42's own WHY comment for the slot-collision history. The previous
-- two US-7.x stories (US-7.1 part-mapping panel; US-7.0 verify gate) used V41/V42 already,
-- so this is the next free slot.
