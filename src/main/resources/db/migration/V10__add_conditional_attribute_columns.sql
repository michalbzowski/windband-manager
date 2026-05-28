-- V10: Add conditional display columns to attribute definitions
-- Allows attributes to be shown only when parent attribute has specific value

-- Uniform attributes
ALTER TABLE uniform_attribute_defs ADD COLUMN IF NOT EXISTS depends_on_attribute_id BIGINT REFERENCES uniform_attribute_defs(id);
ALTER TABLE uniform_attribute_defs ADD COLUMN IF NOT EXISTS depends_on_value VARCHAR(500);

-- Instrument attributes
ALTER TABLE instrument_attribute_defs ADD COLUMN IF NOT EXISTS depends_on_attribute_id BIGINT REFERENCES instrument_attribute_defs(id);
ALTER TABLE instrument_attribute_defs ADD COLUMN IF NOT EXISTS depends_on_value VARCHAR(500);

-- Order attributes (for future use)
ALTER TABLE order_attribute_defs ADD COLUMN IF NOT EXISTS depends_on_attribute_id BIGINT REFERENCES order_attribute_defs(id);
ALTER TABLE order_attribute_defs ADD COLUMN IF NOT EXISTS depends_on_value VARCHAR(500);