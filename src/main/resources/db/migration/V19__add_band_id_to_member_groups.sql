-- V19: Add band_id to member_groups for multi-tenant isolation
-- Groups must belong to a specific band/team

ALTER TABLE IF EXISTS member_groups ADD COLUMN IF NOT EXISTS band_id BIGINT;

-- Assign existing groups to band based on their members' band
-- If a group has members from only one band, assign that band
-- If mixed or no members, assign to band 1 as fallback (should not happen in practice)
UPDATE member_groups g
SET band_id = COALESCE(
    (SELECT DISTINCT m.band_id FROM group_members gm
     JOIN members m ON m.id = gm.member_id
     WHERE gm.group_id = g.id
     LIMIT 1),
    1
)
WHERE g.band_id IS NULL;

-- Make band_id NOT NULL
ALTER TABLE member_groups ALTER COLUMN band_id SET NOT NULL;

-- Add FK constraint and index
ALTER TABLE member_groups ADD CONSTRAINT fk_member_groups_band FOREIGN KEY (band_id) REFERENCES bands(id);
CREATE INDEX IF NOT EXISTS idx_member_groups_band ON member_groups(band_id);
