-- Real group membership.
--
-- Until now `tenant_group.member_count` was a projected number with no backing
-- rows (the evaluation was simulated). This table stores the actual user<->group
-- links so members can be listed, added and removed, and smart groups can be
-- materialised from their rule. `member_count` now reflects COUNT(group_member).
CREATE TABLE group_member (
    id        uuid PRIMARY KEY,
    tenant_id uuid NOT NULL,
    group_id  uuid NOT NULL REFERENCES tenant_group (id) ON DELETE CASCADE,
    user_id   uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    added_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_group_member UNIQUE (group_id, user_id)
);

CREATE INDEX ix_group_member_group ON group_member (group_id);
CREATE INDEX ix_group_member_tenant ON group_member (tenant_id);

-- Row-Level Security: same tenant-isolation pattern as the other tenant tables.
ALTER TABLE group_member ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_member FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON group_member
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

-- Explicit grant (ALTER DEFAULT PRIVILEGES from V2026.07.24.001 also covers new
-- tables, but keep this self-contained).
GRANT SELECT, INSERT, UPDATE, DELETE ON group_member TO digishield_app;
