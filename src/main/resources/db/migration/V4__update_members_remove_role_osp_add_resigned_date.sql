-- V4: update members table - remove role and osp_member, add resigned_date

-- Remove role column
ALTER TABLE IF EXISTS members DROP COLUMN IF EXISTS role;

-- Remove osp_member column
ALTER TABLE IF EXISTS members DROP COLUMN IF EXISTS osp_member;

-- Add resigned_date column
ALTER TABLE IF EXISTS members ADD COLUMN IF NOT EXISTS resigned_date DATE;

-- Make joined_date NOT NULL if it isn't already
UPDATE members SET joined_date = CURRENT_DATE WHERE joined_date IS NULL;
ALTER TABLE members ALTER COLUMN joined_date SET NOT NULL;
