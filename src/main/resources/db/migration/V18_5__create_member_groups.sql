-- V18_5__create_member_groups.sql
-- Creates the base member_groups + group_members tables.
--
-- These were missing from the Flyway chain: V19 does ALTER TABLE member_groups
-- but nothing ever created it. On the Railway prod DB the tables predate Flyway
-- adoption, so V19 succeeded there — but a FRESH database (local docker compose)
-- fails with "relation member_groups does not exist". Runs BEFORE V19.
-- IF NOT EXISTS = safe no-op on any DB where they already exist.
-- Columns match domain.member.Group / domain.member.GroupMember (@Entity).
-- band_id is added as NULLABLE here; V19 backfills it and sets NOT NULL + FK.

CREATE TABLE IF NOT EXISTS member_groups (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL UNIQUE,
    description         VARCHAR(255),
    band_id             BIGINT
);

CREATE TABLE IF NOT EXISTS group_members (
    id        BIGSERIAL PRIMARY KEY,
    group_id  BIGINT NOT NULL REFERENCES member_groups(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    UNIQUE (group_id, member_id)
);

CREATE INDEX IF NOT EXISTS idx_group_members_group ON group_members(group_id);
CREATE INDEX IF NOT EXISTS idx_group_members_member ON group_members(member_id);
