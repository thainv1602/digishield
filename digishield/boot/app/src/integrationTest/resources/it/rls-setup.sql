-- =============================================================================
-- Test-only setup for TenantIsolationIT (Row-Level Security verification).
--
-- WHAT THIS FILE DOES:
--   It (re)creates the `phishing_report` table for each test so the dataset is
--   clean and the RLS policy is in place. The DDL is kept IN SYNC with the real
--   Flyway migration V2026.06.27.001__init.sql: same columns
--   (id, tenant_id, user_id, payload, ai_label, ai_confidence, status), same
--   types (tenant_id is uuid to match PhishingReport.tenantId : UUID,
--   ai_confidence is double precision, payload is text), and the same
--   tenant_isolation policy comparing the uuid tenant_id against app.tenant_id
--   (the GUC is a text string, cast to uuid via ::uuid).
--
--   The IT runs with `spring.flyway.enabled=false` and
--   `spring.jpa.hibernate.ddl-auto=none`, so the test owns the schema via this
--   script (a single table is enough for the isolation test, and recreating it
--   per test keeps the data deterministic). The shape matches the migration so
--   the same SQL would work if the IT were switched to run the real migration.
--
-- WHY `FORCE ROW LEVEL SECURITY`:
--   Testcontainers' default Postgres user ("test") is the table OWNER and is a
--   SUPERUSER. Postgres BYPASSES RLS policies for the owner/superuser unless the
--   table is declared with FORCE ROW LEVEL SECURITY. Without FORCE, the
--   isolation test would pass trivially (every row visible) and prove nothing.
--   FORCE makes the policy apply even to the owning role, so the test verifies
--   genuine tenant isolation.
--
-- The RLS policy filters by the session GUC `app.tenant_id`, which the
-- production RlsTenantAspect sets via set_config('app.tenant_id', ?, true)
-- at the start of every @Transactional method. tenant_id is a uuid, and the
-- GUC (a text string) is cast to uuid (::uuid) so it is comparable to the
-- column. The policy is fail-closed: when app.tenant_id is unset,
-- current_setting(..., true) returns NULL, the ::uuid cast of NULL is NULL, and
-- the USING predicate (tenant_id = NULL) is never true => zero rows visible.
-- =============================================================================

DROP TABLE IF EXISTS phishing_report;

CREATE TABLE phishing_report (
    id              uuid PRIMARY KEY,
    tenant_id       uuid             NOT NULL,
    user_id         uuid             NOT NULL,
    payload         text,
    ai_label        varchar(40),
    ai_confidence   double precision NOT NULL DEFAULT 0,
    status          varchar(40)      NOT NULL,
    reporter        varchar(255),
    subject         varchar(255),
    sender          varchar(255),
    ai_reason       text,
    blacklist_match boolean          NOT NULL DEFAULT false,
    converted_to_training boolean    NOT NULL DEFAULT false,
    reported_at     timestamptz
);

ALTER TABLE phishing_report ENABLE ROW LEVEL SECURITY;
-- Critical: apply RLS even to the table owner / superuser (Testcontainers user).
ALTER TABLE phishing_report FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON phishing_report
    USING (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.tenant_id', true), '')::uuid);
