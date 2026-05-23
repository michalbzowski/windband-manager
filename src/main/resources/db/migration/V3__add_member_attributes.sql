CREATE TABLE IF NOT EXISTS member_attribute_defs (
    id BIGSERIAL PRIMARY KEY,
    band_id BIGINT NOT NULL REFERENCES bands(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(10) NOT NULL DEFAULT 'BOOLEAN' CHECK (type IN ('BOOLEAN', 'TEXT')),
    required BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATE NOT NULL DEFAULT CURRENT_DATE,
    UNIQUE(band_id, name)
);

CREATE TABLE IF NOT EXISTS member_attribute_values (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    attribute_def_id BIGINT NOT NULL REFERENCES member_attribute_defs(id) ON DELETE CASCADE,
    value VARCHAR(500),
    UNIQUE(member_id, attribute_def_id)
);

CREATE INDEX IF NOT EXISTS idx_attr_def_band ON member_attribute_defs(band_id);
CREATE INDEX IF NOT EXISTS idx_attr_val_member ON member_attribute_values(member_id);
CREATE INDEX IF NOT EXISTS idx_attr_val_def ON member_attribute_values(attribute_def_id);

-- Default attributes for band_id=1 (MOD Strażak)
INSERT INTO member_attribute_defs (band_id, name, type, required, display_order, active, created_at) VALUES
(1, 'Członek', 'BOOLEAN', false, 1, true, CURRENT_DATE),
(1, 'Uczy się', 'BOOLEAN', false, 2, true, CURRENT_DATE),
(1, 'Zrezygnował', 'BOOLEAN', false, 3, true, CURRENT_DATE),
(1, 'Członek OSP', 'BOOLEAN', false, 4, true, CURRENT_DATE);
