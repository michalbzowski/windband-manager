-- Add display_in_list column to all attribute definition tables

ALTER TABLE member_attribute_defs ADD COLUMN display_in_list BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE uniform_attribute_defs ADD COLUMN display_in_list BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE instrument_attribute_defs ADD COLUMN display_in_list BOOLEAN NOT NULL DEFAULT FALSE;