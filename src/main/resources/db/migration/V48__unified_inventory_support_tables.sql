-- V48: unified inventory support tables missing from the Flyway chain.
--
-- These @Entity tables (domain.inventory.*) were created ONLY by
-- `spring.jpa.hibernate.ddl-auto: update` — never by Flyway. On the Railway
-- production DB they may exist or not depending on Hibernate bootstrap order;
-- on a fresh database they appear only when Hibernate happens to run before
-- first use. This migration makes the schema explicit and reproducible.
--
-- Tables (columns match @Entity definitions exactly):
--   * warehouses                     (domain.inventory.Warehouse)
--   * warehouse_transfers            (domain.inventory.WarehouseTransfer)
--   * instrument_service_records     (domain.inventory.InstrumentServiceRecord)
--   * private_possession_declarations (domain.inventory.PrivatePossessionDeclaration)
--   * inventory_needs                (domain.inventory.InventoryNeed)
--
-- NOT included: asset_assignment_history — it already exists in the chain
-- (V13_5 create + V14 enhance); column rework to inventory_items.id happens
-- in a later migration after entity unification.
--
-- IF NOT EXISTS makes this a safe no-op where the tables already exist
-- (production baseline safety).

CREATE TABLE IF NOT EXISTS warehouses (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    description     VARCHAR(255),
    type            VARCHAR(50) NOT NULL,
    band_id         BIGINT NOT NULL REFERENCES bands(id),
    address         VARCHAR(255),
    contact_person  VARCHAR(255),
    phone           VARCHAR(255),
    email           VARCHAR(255),
    capacity        INTEGER,
    layout_notes    VARCHAR(255),
    active          BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX IF NOT EXISTS idx_warehouses_band ON warehouses(band_id);
CREATE INDEX IF NOT EXISTS idx_warehouses_type ON warehouses(type);

CREATE TABLE IF NOT EXISTS warehouse_transfers (
    id                  BIGSERIAL PRIMARY KEY,
    item_id             BIGINT NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    from_warehouse_id   BIGINT REFERENCES warehouses(id) ON DELETE SET NULL,
    to_warehouse_id     BIGINT NOT NULL REFERENCES warehouses(id),
    transferred_by_user_id BIGINT NOT NULL REFERENCES app_users(id),
    transfer_date       DATE NOT NULL,
    transfer_datetime   TIMESTAMP NOT NULL,
    reason              VARCHAR(255),
    notes               VARCHAR(255),
    condition_at_transfer VARCHAR(50),
    expected_return_date  DATE,
    transfer_cost       NUMERIC(12,2),
    reference_number    VARCHAR(255),
    band_id             BIGINT NOT NULL REFERENCES bands(id)
);

CREATE INDEX IF NOT EXISTS idx_wh_transfers_item ON warehouse_transfers(item_id);
CREATE INDEX IF NOT EXISTS idx_wh_transfers_to   ON warehouse_transfers(to_warehouse_id);
CREATE INDEX IF NOT EXISTS idx_wh_transfers_band ON warehouse_transfers(band_id);

CREATE TABLE IF NOT EXISTS instrument_service_records (
    id                  BIGSERIAL PRIMARY KEY,
    instrument_id       BIGINT NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    service_type        VARCHAR(50) NOT NULL,
    service_date        DATE NOT NULL,
    completed_date      DATE,
    service_provider    VARCHAR(255) NOT NULL,
    provider_contact    VARCHAR(255),
    description         TEXT NOT NULL,
    parts_replaced      TEXT,
    cost                NUMERIC(12,2),
    warranty_until      DATE,
    priority            VARCHAR(50),
    status              VARCHAR(50) NOT NULL,
    notes               TEXT,
    next_service_date   DATE,
    next_service_type   VARCHAR(50),
    requested_by_user_id BIGINT NOT NULL REFERENCES app_users(id),
    approved_by_user_id  BIGINT REFERENCES app_users(id),
    approved_at         DATE,
    completed_by_user_id BIGINT REFERENCES app_users(id),
    completed_at        TIMESTAMP,
    band_id             BIGINT NOT NULL REFERENCES bands(id)
);

CREATE INDEX IF NOT EXISTS idx_service_records_instrument ON instrument_service_records(instrument_id);
CREATE INDEX IF NOT EXISTS idx_service_records_status     ON instrument_service_records(status);
CREATE INDEX IF NOT EXISTS idx_service_records_next       ON instrument_service_records(next_service_date);
CREATE INDEX IF NOT EXISTS idx_service_records_band       ON instrument_service_records(band_id);

CREATE TABLE IF NOT EXISTS private_possession_declarations (
    id                  BIGSERIAL PRIMARY KEY,
    member_id           BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    external_owner_type VARCHAR(50) NOT NULL,
    external_owner_name VARCHAR(255),
    external_owner_contact VARCHAR(255),
    item_name           VARCHAR(255) NOT NULL,
    item_description    TEXT,
    brand               VARCHAR(255),
    model               VARCHAR(255),
    serial_number       VARCHAR(255),
    item_type           VARCHAR(50) NOT NULL,
    condition           VARCHAR(50),
    declared_date       DATE NOT NULL,
    valid_from          DATE,
    valid_until         DATE,
    estimated_value     NUMERIC(12,2),
    notes               TEXT,
    document_paths      TEXT,
    declared_by_user_id  BIGINT NOT NULL REFERENCES app_users(id),
    verified_by_user_id  BIGINT REFERENCES app_users(id),
    verified_at         DATE,
    status              VARCHAR(50) NOT NULL,
    band_id             BIGINT NOT NULL REFERENCES bands(id)
);

CREATE INDEX IF NOT EXISTS idx_pp_decl_member ON private_possession_declarations(member_id);
CREATE INDEX IF NOT EXISTS idx_pp_decl_status ON private_possession_declarations(status);
CREATE INDEX IF NOT EXISTS idx_pp_decl_band   ON private_possession_declarations(band_id);

CREATE TABLE IF NOT EXISTS inventory_needs (
    id                      BIGSERIAL PRIMARY KEY,
    item_type               VARCHAR(50) NOT NULL,
    item_name               VARCHAR(255) NOT NULL,
    item_description        TEXT,
    desired_brand           VARCHAR(255),
    desired_model           VARCHAR(255),
    desired_size            VARCHAR(255),
    desired_color           VARCHAR(255),
    quantity                INTEGER NOT NULL DEFAULT 1,
    estimated_unit_cost     NUMERIC(12,2),
    estimated_total_cost    NUMERIC(12,2),
    priority                VARCHAR(50),
    status                  VARCHAR(50) NOT NULL,
    requested_by_member_id  BIGINT NOT NULL REFERENCES members(id),
    requested_by_user_id    BIGINT NOT NULL REFERENCES app_users(id),
    approved_by_user_id     BIGINT REFERENCES app_users(id),
    approved_at             DATE,
    rejection_reason        TEXT,
    preferred_supplier      VARCHAR(255),
    supplier_contact        VARCHAR(255),
    supplier_quote_reference VARCHAR(255),
    order_number            VARCHAR(255),
    ordered_at              DATE,
    expected_delivery_date  DATE,
    delivered_at            DATE,
    completed_at            DATE,
    cancelled_at            DATE,
    cancellation_reason     TEXT,
    notes                   TEXT,
    band_id                 BIGINT NOT NULL REFERENCES bands(id),
    attribute_values        TEXT
);

CREATE INDEX IF NOT EXISTS idx_inv_needs_band   ON inventory_needs(band_id);
CREATE INDEX IF NOT EXISTS idx_inv_needs_status ON inventory_needs(status);
CREATE INDEX IF NOT EXISTS idx_inv_needs_member ON inventory_needs(requested_by_member_id);
