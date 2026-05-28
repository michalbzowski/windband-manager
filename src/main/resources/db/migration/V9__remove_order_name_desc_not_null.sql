-- V9: Make item_name and description nullable in inventory_orders
-- These columns are no longer required since order type determines the item category
ALTER TABLE inventory_orders ALTER COLUMN item_name DROP NOT NULL;
ALTER TABLE inventory_orders ALTER COLUMN description DROP NOT NULL;