-- V16__add_superset_uuid.sql
-- Add UUID column for Superset dashboard (required by embedded SDK)

ALTER TABLE superset_dashboards ADD COLUMN IF NOT EXISTS superset_uuid VARCHAR(36) NOT NULL DEFAULT '';

-- Backfill UUID from existing data (will be populated on next sync)
-- The default empty string is a placeholder; sync will update it

CREATE INDEX IF NOT EXISTS idx_superset_dashboards_uuid ON superset_dashboards(superset_uuid);
