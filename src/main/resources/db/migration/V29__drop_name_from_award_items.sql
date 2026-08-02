-- Remove the mandatory 'name' column from award_items
-- Everything is now attribute-based

ALTER TABLE award_items DROP COLUMN IF EXISTS name;