-- V15__superset_dashboard_management.sql
-- Tables for managing Superset dashboard assignments to bands/teams

CREATE TABLE IF NOT EXISTS superset_dashboards (
    id              BIGSERIAL PRIMARY KEY,
    superset_id     INTEGER NOT NULL UNIQUE,
    title           VARCHAR(255) NOT NULL,
    slug            VARCHAR(255) NOT NULL,
    description     VARCHAR(1000),
    icon            VARCHAR(64),
    position        INTEGER NOT NULL DEFAULT 0,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    is_embedded     BOOLEAN NOT NULL DEFAULT TRUE,
    first_synced_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_synced_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_superset_dashboards_active ON superset_dashboards(is_active);
CREATE INDEX idx_superset_dashboards_position ON superset_dashboards(position);

CREATE TABLE IF NOT EXISTS dashboard_band_assignments (
    id                  BIGSERIAL PRIMARY KEY,
    dashboard_id        BIGINT NOT NULL REFERENCES superset_dashboards(id) ON DELETE CASCADE,
    band_id             BIGINT NOT NULL REFERENCES bands(id) ON DELETE CASCADE,
    auto_assign_new     BOOLEAN NOT NULL DEFAULT FALSE,
    assigned_at         TIMESTAMP NOT NULL DEFAULT NOW(),
    assigned_by_user_id BIGINT REFERENCES app_users(id),
    CONSTRAINT uq_dashboard_band UNIQUE (dashboard_id, band_id)
);

CREATE INDEX idx_dba_band ON dashboard_band_assignments(band_id);
CREATE INDEX idx_dba_dashboard ON dashboard_band_assignments(dashboard_id);
CREATE INDEX idx_dba_auto_assign ON dashboard_band_assignments(auto_assign_new) WHERE auto_assign_new = TRUE;
