-- V14__enhance_asset_assignment_history.sql
-- Add assigned_by_user_id, condition_at_assign, condition_at_return to asset_assignment_history
-- Enables full audit trail: who assigned, condition at assignment and return time

ALTER TABLE asset_assignment_history
    ADD COLUMN assigned_by_user_id BIGINT REFERENCES app_users(id);

ALTER TABLE asset_assignment_history
    ADD COLUMN condition_at_assign VARCHAR(255);

ALTER TABLE asset_assignment_history
    ADD COLUMN condition_at_return VARCHAR(255);
