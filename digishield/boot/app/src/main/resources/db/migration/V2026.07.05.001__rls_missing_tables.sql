-- Backfill Row-Level Security on tenant-scoped tables that were created without
-- it in earlier migrations. Each table already carries a `tenant_id` column and
-- was isolated only at the application layer; this adds the same DB-enforced
-- policy the other tenant tables use, so a query that forgets to scope by tenant
-- can no longer leak rows across tenants.
--
-- Pattern (identical to the existing tenant_isolation policies):
--   ENABLE + FORCE ROW LEVEL SECURITY, then a policy comparing tenant_id to the
--   `app.tenant_id` GUC that RlsTenantAspect sets per transaction. FORCE makes it
--   apply even to the table owner. DROP POLICY IF EXISTS keeps this re-runnable.

ALTER TABLE ai_template     ENABLE ROW LEVEL SECURITY;
ALTER TABLE ai_template     FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON ai_template;
CREATE POLICY tenant_isolation ON ai_template
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE assessment      ENABLE ROW LEVEL SECURITY;
ALTER TABLE assessment      FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON assessment;
CREATE POLICY tenant_isolation ON assessment
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE coaching_page   ENABLE ROW LEVEL SECURITY;
ALTER TABLE coaching_page   FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON coaching_page;
CREATE POLICY tenant_isolation ON coaching_page
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE threat_intel    ENABLE ROW LEVEL SECURITY;
ALTER TABLE threat_intel    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON threat_intel;
CREATE POLICY tenant_isolation ON threat_intel
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE subscription    ENABLE ROW LEVEL SECURITY;
ALTER TABLE subscription    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON subscription;
CREATE POLICY tenant_isolation ON subscription
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tenant_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_settings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_settings;
CREATE POLICY tenant_isolation ON tenant_settings
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE tenant_group    ENABLE ROW LEVEL SECURITY;
ALTER TABLE tenant_group    FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON tenant_group;
CREATE POLICY tenant_isolation ON tenant_group
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

ALTER TABLE usage_metering  ENABLE ROW LEVEL SECURITY;
ALTER TABLE usage_metering  FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON usage_metering;
CREATE POLICY tenant_isolation ON usage_metering
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);
