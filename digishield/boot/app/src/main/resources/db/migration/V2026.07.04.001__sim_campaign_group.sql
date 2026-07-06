-- =============================================================================
-- simulation: sim_campaign gained the target audience group chosen in the wizard
--
-- The campaign wizard's audience step (POST /sim/campaigns) now persists the
-- selected group. Nullable so legacy/seed campaigns without a group stay valid.
-- =============================================================================
ALTER TABLE sim_campaign ADD COLUMN IF NOT EXISTS group_id uuid;
