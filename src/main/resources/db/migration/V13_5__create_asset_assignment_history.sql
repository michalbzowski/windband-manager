-- V13_5__create_asset_assignment_history.sql
-- Creates the base asset_assignment_history table.
--
-- This CREATE was missing from the migration chain: V14 does ALTER TABLE
-- asset_assignment_history but no earlier migration ever created it. On the
-- Railway production DB the table already existed (schema baselined before
-- Flyway), so V14 succeeded there — but on a FRESH database (e.g. a local
-- `docker compose up`) V14 failed with "relation asset_assignment_history
-- does not exist". This migration fills that gap and runs BEFORE V14.
--
-- IF NOT EXISTS makes it a safe no-op on any DB where the table already
-- exists (production baseline safety). The three audit columns
-- (assigned_by_user_id, condition_at_assign, condition_at_return) are added
-- by V14 and intentionally omitted here.
--
-- Column set matches domain.inventory.AssetAssignmentHistory (@Entity).

CREATE TABLE IF NOT EXISTS asset_assignment_history (
    id                BIGSERIAL PRIMARY KEY,
    uniform_item_id   BIGINT REFERENCES uniform_items(id) ON DELETE CASCADE,
    instrument_item_id BIGINT REFERENCES instrument_items(id) ON DELETE CASCADE,
    member_id         BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    assigned_at       DATE NOT NULL,
    returned_at       DATE,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    notes             VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_asset_assignment_history_member
    ON asset_assignment_history(member_id);
CREATE INDEX IF NOT EXISTS idx_asset_assignment_history_uniform_item
    ON asset_assignment_history(uniform_item_id);
CREATE INDEX IF NOT EXISTS idx_asset_assignment_history_instrument_item
    ON asset_assignment_history(instrument_item_id);
CREATE INDEX IF NOT EXISTS idx_asset_assignment_history_active
    ON asset_assignment_history(active);
