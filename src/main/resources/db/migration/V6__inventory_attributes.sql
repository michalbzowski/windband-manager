-- V6: Create attribute tables for Uniform, Instrument, and Order entities
-- Member attribute types already extended in previous migration

-- Inventory orders table (custom orders for uniforms/instruments)
CREATE TABLE IF NOT EXISTS inventory_orders (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id),
    order_number VARCHAR(100),
    order_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    notes TEXT,
    attributes_json TEXT
);

-- Uniform attribute definitions
CREATE TABLE IF NOT EXISTS uniform_attribute_defs (
    id BIGSERIAL PRIMARY KEY,
    band_id BIGINT NOT NULL REFERENCES bands(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('BOOLEAN','TEXT','NUMBER','SELECT','MULTI_SELECT','DATE')),
    required BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at DATE NOT NULL DEFAULT CURRENT_DATE,
    options VARCHAR(2000),
    UNIQUE (band_id, name)
);

-- Uniform attribute values
CREATE TABLE IF NOT EXISTS uniform_attribute_values (
    id BIGSERIAL PRIMARY KEY,
    uniform_item_id BIGINT NOT NULL REFERENCES uniform_items(id),
    attribute_def_id BIGINT NOT NULL REFERENCES uniform_attribute_defs(id),
    value TEXT,
    UNIQUE (uniform_item_id, attribute_def_id)
);

-- Instrument attribute definitions
CREATE TABLE IF NOT EXISTS instrument_attribute_defs (
    id BIGSERIAL PRIMARY KEY,
    band_id BIGINT NOT NULL REFERENCES bands(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('BOOLEAN','TEXT','NUMBER','SELECT','MULTI_SELECT','DATE')),
    required BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at DATE NOT NULL DEFAULT CURRENT_DATE,
    options VARCHAR(2000),
    UNIQUE (band_id, name)
);

-- Instrument attribute values
CREATE TABLE IF NOT EXISTS instrument_attribute_values (
    id BIGSERIAL PRIMARY KEY,
    instrument_item_id BIGINT NOT NULL REFERENCES instrument_items(id),
    attribute_def_id BIGINT NOT NULL REFERENCES instrument_attribute_defs(id),
    value TEXT,
    UNIQUE (instrument_item_id, attribute_def_id)
);

-- Order attribute definitions
CREATE TABLE IF NOT EXISTS order_attribute_defs (
    id BIGSERIAL PRIMARY KEY,
    band_id BIGINT NOT NULL REFERENCES bands(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL CHECK (type IN ('BOOLEAN','TEXT','NUMBER','SELECT','MULTI_SELECT','DATE')),
    required BOOLEAN NOT NULL DEFAULT false,
    display_order INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at DATE NOT NULL DEFAULT CURRENT_DATE,
    options VARCHAR(2000),
    UNIQUE (band_id, name)
);

-- Order attribute values
CREATE TABLE IF NOT EXISTS order_attribute_values (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES inventory_orders(id),
    attribute_def_id BIGINT NOT NULL REFERENCES order_attribute_defs(id),
    value TEXT,
    UNIQUE (order_id, attribute_def_id)
);

-- Add order_number to uniform_items
ALTER TABLE uniform_items ADD COLUMN IF NOT EXISTS order_number VARCHAR(100);

-- Add order_number to instrument_items
ALTER TABLE instrument_items ADD COLUMN IF NOT EXISTS order_number VARCHAR(100);
