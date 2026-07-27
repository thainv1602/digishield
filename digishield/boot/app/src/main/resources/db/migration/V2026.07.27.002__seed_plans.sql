-- The three subscription plans, as reference data.
--
-- `plan` carries no tenant_id: these describe what the platform offers, not
-- what any one customer has. Until now the only writer was a @Profile("dev")
-- seeder, so a real deployment had an empty table and the plan screen listed
-- nothing — the same shape as department_risk and sim_result.
--
-- Reference data belongs in a migration rather than an endpoint: there is no
-- per-tenant answer to seed, and an admin should not have to type the platform's
-- own price list in before the product works.
--
-- Ids match the dev seeder's, so dev and production agree on which plan is
-- which. ON CONFLICT keeps this idempotent and leaves any hand-edited limits
-- alone.
INSERT INTO plan (id, name, limits_json, features_json) VALUES
    ('22222222-0000-0000-0000-0000000000ed', 'edu',
     '{"seats":2000,"emails":50000,"ai_calls":5000}',
     '{"deepfake_sim":false,"training":true,"soc_console":false}'),
    ('22222222-0000-0000-0000-0000000000b5', 'business',
     '{"seats":10000,"emails":500000,"ai_calls":50000}',
     '{"deepfake_sim":true,"training":true,"soc_console":true}'),
    ('22222222-0000-0000-0000-0000000000a0', 'gov',
     '{"seats":50000,"emails":2000000,"ai_calls":200000}',
     '{"deepfake_sim":true,"training":true,"soc_console":true}')
ON CONFLICT (id) DO NOTHING;
