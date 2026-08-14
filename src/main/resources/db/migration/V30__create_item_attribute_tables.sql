-- V30: Create unified inventory attribute tables with H2-compatible column names
-- H2 treats 'value' as a reserved keyword, so we use 'value_text' instead
-- These tables are used by the unified ItemAttributeDef and ItemAttributeValue entities

-- Item attribute definitions
CREATE TABLE IF NOT EXISTS item_attribute_defs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    attribute_type VARCHAR(50) NOT NULL,
    item_type VARCHAR(50) NOT NULL,
    band_id BIGINT,
    display_in_list BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    is_filterable BOOLEAN NOT NULL DEFAULT TRUE,
    is_conditional BOOLEAN NOT NULL DEFAULT FALSE,
    depends_on_def_id BIGINT,
    conditional_value VARCHAR(255),
    validation_regex TEXT,
    validation_message VARCHAR(500),
    default_value VARCHAR(255),
    options TEXT,
    depends_on_attribute VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_item_attribute_defs_band FOREIGN KEY (band_id) REFERENCES bands(id)
);

CREATE INDEX IF NOT EXISTS idx_item_attribute_defs_band_id ON item_attribute_defs(band_id);
CREATE INDEX IF NOT EXISTS idx_item_attribute_defs_item_type ON item_attribute_defs(item_type);

-- Item attribute values
CREATE TABLE IF NOT EXISTS item_attribute_values (
    id BIGSERIAL PRIMARY KEY,
    item_id BIGINT NOT NULL,
    attribute_def_id BIGINT NOT NULL,
    value_text TEXT,
    value_integer BIGINT,
    value_decimal DOUBLE PRECISION,
    value_boolean BOOLEAN,
    value_date DATE,
    value_file_path VARCHAR(255),
    CONSTRAINT fk_item_attribute_values_item FOREIGN KEY (item_id) REFERENCES inventory_items(id),
    CONSTRAINT fk_item_attribute_values_attribute_def FOREIGN KEY (attribute_def_id) REFERENCES item_attribute_defs(id)
);

CREATE INDEX IF NOT EXISTS idx_item_attribute_values_item_id ON item_attribute_values(item_id);
CREATE INDEX IF NOT EXISTS idx_item_attribute_values_attribute_def_id ON item_attribute_values(attribute_def_id);