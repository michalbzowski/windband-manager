-- V5: Extend attribute types and add options column

-- Add new types to member_attribute_defs
ALTER TABLE member_attribute_defs
    DROP CONSTRAINT IF EXISTS member_attribute_defs_type_check;

ALTER TABLE member_attribute_defs
    ADD CONSTRAINT member_attribute_defs_type_check
    CHECK (type IN ('BOOLEAN', 'TEXT', 'NUMBER', 'SELECT', 'MULTI_SELECT', 'DATE'));

-- Add options column for SELECT/MULTI_SELECT types (JSON array of options)
ALTER TABLE member_attribute_defs
    ADD COLUMN IF NOT EXISTS options VARCHAR(2000);

-- Add default display attributes for band_id=1
INSERT INTO member_attribute_defs (band_id, name, type, required, display_order, active, created_at) VALUES
(1, 'Wzrost', 'NUMBER', false, 10, true, CURRENT_DATE),
(1, 'Rozmiar kołnierzyka', 'NUMBER', false, 11, true, CURRENT_DATE),
(1, 'Gość', 'BOOLEAN', false, 12, true, CURRENT_DATE),
(1, 'Messenger', 'TEXT', false, 13, true, CURRENT_DATE)
ON CONFLICT (band_id, name) DO NOTHING;
