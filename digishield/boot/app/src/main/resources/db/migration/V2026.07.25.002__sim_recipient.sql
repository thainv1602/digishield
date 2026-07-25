-- Per-recipient tracking rows for a launched simulation campaign.
--
-- When a manager "sends" a campaign, one row is created per targeted user with
-- a random token (the row id). The token is embedded in that user's tracking
-- link (GET /api/v1/sim/track/{token}); following it records a CLICK event and
-- stamps clicked_at. This is what turns a DRAFT campaign into real, trackable
-- deliveries instead of a static record.
CREATE TABLE sim_recipient (
    id           uuid PRIMARY KEY,             -- also the opaque tracking token
    tenant_id    uuid NOT NULL,
    campaign_id  uuid NOT NULL,
    user_id      uuid NOT NULL,
    delivered_at timestamptz NOT NULL DEFAULT now(),
    clicked_at   timestamptz
);

CREATE INDEX ix_sim_recipient_campaign ON sim_recipient (campaign_id);
CREATE INDEX ix_sim_recipient_tenant ON sim_recipient (tenant_id);

-- Row-Level Security: same tenant-isolation pattern as the other tenant tables.
-- The public tracking endpoint resolves a token via a superuser JdbcTemplate
-- read (which bypasses RLS); all app reads/writes go through the app role and
-- are tenant-scoped by this policy.
ALTER TABLE sim_recipient ENABLE ROW LEVEL SECURITY;
ALTER TABLE sim_recipient FORCE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sim_recipient
    USING (tenant_id = current_setting('app.tenant_id', true)::uuid)
    WITH CHECK (tenant_id = current_setting('app.tenant_id', true)::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON sim_recipient TO digishield_app;
