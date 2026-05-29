-- V7: Add attributes_json column to inventory_orders
ALTER TABLE inventory_orders ADD COLUMN IF NOT EXISTS attributes_json TEXT;