-- Non-superuser role used to ENFORCE Row-Level Security at runtime.
--
-- The app connects to PostgreSQL as the owner/superuser `digishield` (so Flyway
-- migrations and the demo seeders can run). PostgreSQL bypasses RLS for
-- superusers and table owners-with-BYPASSRLS, which is why FORCE ROW LEVEL
-- SECURITY alone did not isolate tenants in the pgdemo setup.
--
-- To get real isolation without splitting DB credentials, RlsTenantAspect does
-- `SET LOCAL ROLE digishield_app` inside each request transaction (alongside the
-- `app.tenant_id` GUC). Within that transaction the effective role is a plain,
-- NOSUPERUSER / NOBYPASSRLS role, so the tenant_isolation policies apply. Flyway
-- and the seeders never SET ROLE, so they keep running as the superuser.
--
-- The role has NOLOGIN: it is only ever reached via SET ROLE from the connected
-- superuser, so it needs no password/secret. Idempotent so re-runs are safe.

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'digishield_app') THEN
    CREATE ROLE digishield_app NOLOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE;
  END IF;
END
$$;

-- The connected superuser must be a member of the role to SET ROLE to it.
GRANT digishield_app TO CURRENT_USER;

-- Runtime privileges on everything created so far, plus anything future
-- migrations create as this superuser.
GRANT USAGE ON SCHEMA public TO digishield_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO digishield_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO digishield_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO digishield_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO digishield_app;
