-- V16: Add system_admin column for super admin users
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS system_admin BOOLEAN NOT NULL DEFAULT FALSE;
