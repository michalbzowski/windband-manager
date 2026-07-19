-- Extend dynamic groups to support non-attribute sources (e.g. the fixed
-- member "active" field). Previously a dynamic group was always derived from a
-- MemberAttributeDef referenced by dynamic_source_id. We now also allow a
-- (dynamic_source_type, dynamic_source_key) pair so a group can be derived from
-- a first-class member field without converting it into a custom attribute.

ALTER TABLE member_groups ADD COLUMN dynamic_source_type VARCHAR(20);
ALTER TABLE member_groups ADD COLUMN dynamic_source_key VARCHAR(255);

-- Backfill legacy attribute-backed groups: any group that already has a
-- dynamic_source_id is an ATTRIBUTE-backed group. Storing the discriminator
-- keeps the new model consistent and lets isDynamic() / resolveSource() work
-- uniformly going forward.
UPDATE member_groups
SET dynamic_source_type = 'ATTRIBUTE'
WHERE dynamic_source_id IS NOT NULL;
