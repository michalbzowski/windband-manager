-- V28_5__create_award_items.sql
-- Creates the base award_items + award_attribute_defs + award_attribute_values
-- tables. These were missing from the Flyway chain: V29 does
-- ALTER TABLE award_items DROP COLUMN name, and V31 renames
-- award_attribute_values."value" -> value_text — but nothing ever created them.
-- On the Railway prod DB they predate Flyway; a FRESH local DB fails.
-- Runs BEFORE V29/V31. IF NOT EXISTS = safe no-op where they already exist.
-- Columns match domain.inventory.AwardItem / AwardAttributeDef / AwardAttributeValue.
--
-- NOTE: award_items still has the legacy `name` column here (V29 drops it),
-- and award_attribute_values uses the legacy `value` column name (V31 renames
-- it to value_text). This keeps the later ALTER migrations valid.

CREATE TABLE IF NOT EXISTS award_items (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255),
    description  VARCHAR(500),
    member_id    BIGINT REFERENCES members(id) ON DELETE SET NULL,
    band_id      BIGINT NOT NULL REFERENCES bands(id),
    date_awarded DATE,
    order_number VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_award_items_band ON award_items(band_id);
CREATE INDEX IF NOT EXISTS idx_award_items_member ON award_items(member_id);

CREATE TABLE IF NOT EXISTS award_attribute_defs (
    id                      BIGSERIAL PRIMARY KEY,
    band_id                 BIGINT NOT NULL REFERENCES bands(id),
    name                    VARCHAR(255) NOT NULL,
    type                    VARCHAR(50) NOT NULL,
    required                BOOLEAN NOT NULL DEFAULT FALSE,
    display_order           INTEGER NOT NULL DEFAULT 0,
    active                  BOOLEAN NOT NULL DEFAULT TRUE,
    display_in_list         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              DATE NOT NULL DEFAULT CURRENT_DATE,
    options                 VARCHAR(2000),
    depends_on_attribute_id BIGINT,
    depends_on_value        VARCHAR(255),
    UNIQUE (band_id, name)
);

CREATE TABLE IF NOT EXISTS award_attribute_values (
    id               BIGSERIAL PRIMARY KEY,
    award_item_id    BIGINT NOT NULL REFERENCES award_items(id) ON DELETE CASCADE,
    attribute_def_id BIGINT NOT NULL REFERENCES award_attribute_defs(id) ON DELETE CASCADE,
    "value"          TEXT,
    UNIQUE (award_item_id, attribute_def_id)
);
