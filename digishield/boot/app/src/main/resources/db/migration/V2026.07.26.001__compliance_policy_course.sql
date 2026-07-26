-- Compliance completion is now derived, not typed in.
--
-- `completion_pct` was written once when a policy was created and never updated
-- by anything, so the whole Compliance screen — the average, the compliant /
-- overdue head counts, the "completed" and "due soon" tallies — was reporting a
-- frozen manual number while sitting next to a real head count. Drop it and link
-- a policy to the course that satisfies it instead; completion is computed from
-- that course's enrollments at read time.
--
-- `course_id` is nullable on purpose: a policy that maps to no single course
-- (e.g. a broad legal framework) falls back to the tenant's overall mandatory
-- training completion rather than being dropped from the report.

ALTER TABLE compliance_policy ADD COLUMN IF NOT EXISTS course_id uuid;

ALTER TABLE compliance_policy DROP COLUMN IF EXISTS completion_pct;
