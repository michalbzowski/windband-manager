-- V21: Dynamic groups backed by BOOLEAN member attributes
-- A nullable FK from member_groups → member_attribute_defs.
-- When non-null, the group is "dynamic" — its members are auto-managed
-- by the sync logic in GroupCommandService (driven by MemberAttributeCommandService).

ALTER TABLE member_groups
    ADD COLUMN dynamic_source_id BIGINT;

ALTER TABLE member_groups
    ADD CONSTRAINT fk_member_groups_dynamic_source
        FOREIGN KEY (dynamic_source_id)
        REFERENCES member_attribute_defs (id)
        ON DELETE CASCADE;

-- 1:1 enforcement: a single attribute def can back at most one dynamic group.
ALTER TABLE member_groups
    ADD CONSTRAINT uq_member_groups_dynamic_source
        UNIQUE (dynamic_source_id);
