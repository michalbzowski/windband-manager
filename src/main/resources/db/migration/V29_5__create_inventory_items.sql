-- V29_5__create_inventory_items.sql
-- Creates the base inventory_items table (single-table inheritance for all
-- unified inventory item types). Missing from the Flyway chain: V30 adds
-- item_attribute_values with an FK REFERENCES inventory_items(id), but nothing
-- ever created inventory_items. On the Railway prod DB it predates Flyway; a
-- FRESH local DB fails with "relation inventory_items does not exist".
-- Runs BEFORE V30. IF NOT EXISTS = safe no-op where it already exists.
-- Columns match domain.inventory.InventoryItem (@Entity, SINGLE_TABLE,
-- @DiscriminatorColumn item_type).

CREATE TABLE IF NOT EXISTS inventory_items (
    id                        BIGSERIAL PRIMARY KEY,
    item_type                 VARCHAR(50) NOT NULL,
    name                      VARCHAR(255) NOT NULL,
    description               VARCHAR(500),
    member_id                 BIGINT REFERENCES members(id) ON DELETE SET NULL,
    ownership_status          VARCHAR(50) NOT NULL,
    lifecycle_status          VARCHAR(50) NOT NULL,
    band_id                   BIGINT NOT NULL REFERENCES bands(id),
    order_number              VARCHAR(255),
    system_id                 VARCHAR(255) UNIQUE,
    external_inventory_number VARCHAR(255),
    external_owner_type       VARCHAR(50),
    external_owner_name       VARCHAR(255),
    serial_number             VARCHAR(255),
    manufacturer              VARCHAR(255),
    model                     VARCHAR(255),
    purchase_date             DATE,
    purchase_cost             NUMERIC(12,2),
    condition                 VARCHAR(50),
    notes                     TEXT,
    unit                      VARCHAR(255),
    warehouse_id              BIGINT,
    source_need_id            BIGINT
);

CREATE INDEX IF NOT EXISTS idx_inventory_items_band ON inventory_items(band_id);
CREATE INDEX IF NOT EXISTS idx_inventory_items_member ON inventory_items(member_id);
CREATE INDEX IF NOT EXISTS idx_inventory_items_item_type ON inventory_items(item_type);
