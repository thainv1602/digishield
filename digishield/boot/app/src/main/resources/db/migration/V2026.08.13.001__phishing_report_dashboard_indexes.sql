-- Index the two phishing_report reads the admin dashboard makes on every load.
--
-- Until now the table carried only idx_phishing_report_tenant (tenant_id), and
-- both dashboard panels were computed in Java over every report a tenant had
-- ever filed: the open-alert tile counted them, and the recent-reports panel
-- sorted them to keep six. Both are now expressed as queries, and each needs a
-- little more than tenant_id to be answered without reading the whole tenant.
--
-- The open-alert tile filters on status and groups by ai_label, so all three
-- columns sit in one index and the count can be answered from it alone. The
-- recent-reports panel takes the newest few, so reported_at is indexed in the
-- order the query reads it; NULLS LAST matches Postgres's default for DESC and
-- keeps the rows predating the column out of the way of the head of the list.
--
-- Both are plain b-trees behind the tenant key, so they stay compatible with
-- the row-level security policy on this table (tenant_id is still the leading
-- column, which is what every policy-filtered query starts from).
CREATE INDEX IF NOT EXISTS idx_phishing_report_tenant_status_label
    ON phishing_report (tenant_id, status, ai_label);

CREATE INDEX IF NOT EXISTS idx_phishing_report_tenant_reported_at
    ON phishing_report (tenant_id, reported_at DESC NULLS LAST);
