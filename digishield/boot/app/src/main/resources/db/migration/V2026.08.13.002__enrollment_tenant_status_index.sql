-- Index the enrollment counts behind the dashboard's training-completion tile.
--
-- The tile is a share: completed enrollments over all of them. It used to be
-- computed by loading every enrollment a tenant had, plus every course so the
-- views could be labelled, and counting in Java. It is now two counts, and the
-- completed one filters on status.
--
-- enrollment carried only idx_enrollment_tenant (tenant_id), so the filtered
-- count had to read all of the tenant's rows to discard most of them. Leading
-- with tenant_id keeps this usable by the row-level security policy, which
-- every query on this table is filtered by.
CREATE INDEX IF NOT EXISTS idx_enrollment_tenant_status
    ON enrollment (tenant_id, status);
