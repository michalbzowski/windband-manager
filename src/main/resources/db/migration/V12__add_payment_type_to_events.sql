ALTER TABLE band_events ADD COLUMN payment_type VARCHAR(20) NOT NULL DEFAULT 'FREE';
ALTER TABLE band_events ADD COLUMN payment_amount DECIMAL(10,2);
