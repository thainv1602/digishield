-- Badge catalog: per-tenant badge definitions that admins manage.
--
-- The existing `badge` table holds per-user badge instances (earned/awarded).
-- This table holds the catalog of badge *definitions* a tenant offers, which the
-- Gamification admin screen lists and adds to.
CREATE TABLE badge_catalog (
    id          uuid PRIMARY KEY,
    tenant_id   uuid NOT NULL,
    name        varchar(200) NOT NULL,
    description varchar(500),
    icon_ref    varchar(50),
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_badge_catalog_tenant ON badge_catalog (tenant_id);

-- Row-Level Security: same tenant-isolation pattern as the other tenant tables.
ALTER TABLE badge_catalog ENABLE ROW LEVEL SECURITY;
ALTER TABLE badge_catalog FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON badge_catalog
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON badge_catalog TO digishield_app;
