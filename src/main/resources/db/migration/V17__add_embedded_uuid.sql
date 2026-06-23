-- V17__add_embedded_uuid.sql
-- Add embedded_uuid column for Superset embedded dashboard SDK

ALTER TABLE superset_dashboards ADD COLUMN IF NOT EXISTS embedded_uuid VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_superset_dashboards_embedded_uuid ON superset_dashboards(embedded_uuid);
