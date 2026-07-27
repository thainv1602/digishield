-- What a badge is for, in a form something can evaluate.
--
-- The catalogue held name, description and icon only. The seeded badges
-- described real conditions in their descriptions — "Hoàn thành 3 khoá học đầu
-- tiên", "Báo cáo đúng 5 email mô phỏng" — but nothing could read them, so no
-- badge was ever awarded and `badge` had one writer: a dev seeder.
--
-- Two columns rather than a JSON blob. The space of criteria is small and the
-- evaluator has to understand every one of them anyway; a blob would accept
-- anything and fail at award time instead of at entry.
--
-- Nullable: a badge with no criteria is one nothing awards automatically, which
-- is the correct reading of every row that exists today.
ALTER TABLE badge_catalog
    ADD COLUMN IF NOT EXISTS criteria_type      varchar(40),
    ADD COLUMN IF NOT EXISTS criteria_threshold integer;

COMMENT ON COLUMN badge_catalog.criteria_type IS
    'COURSES_COMPLETED | REPORTS_CONFIRMED | POINTS; NULL means the badge is not awarded automatically.';
COMMENT ON COLUMN badge_catalog.criteria_threshold IS
    'Value the measure must reach for the badge to be awarded.';
