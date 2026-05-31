-- V12__add_payment_type_to_events.sql
-- Add payment type and amount columns to band_events table

ALTER TABLE band_events 
ADD COLUMN payment_type VARCHAR(20) NOT NULL DEFAULT 'FREE',
ADD COLUMN payment_amount NUMERIC(10, 2);

-- Update default value for existing records
UPDATE band_events SET payment_type = 'FREE' WHERE payment_type IS NULL;