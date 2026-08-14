-- V31: Rename 'value' column to 'value_text' in attribute value tables
-- H2 treats 'value' as a reserved keyword, so we use 'value_text' instead
-- This migration aligns the database schema with the entity mappings

-- Uniform attribute values
ALTER TABLE uniform_attribute_values RENAME COLUMN "value" TO value_text;

-- Instrument attribute values
ALTER TABLE instrument_attribute_values RENAME COLUMN "value" TO value_text;

-- Award attribute values
ALTER TABLE award_attribute_values RENAME COLUMN "value" TO value_text;

-- Order attribute values
ALTER TABLE order_attribute_values RENAME COLUMN "value" TO value_text;

-- Member attribute values
ALTER TABLE member_attribute_values RENAME COLUMN "value" TO value_text;
