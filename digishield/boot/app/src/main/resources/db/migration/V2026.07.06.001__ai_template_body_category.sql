-- Content Studio authoring: store the actual message body and a free-text
-- theme/category on simulation templates (previously only a subject + body_ref
-- slug were kept). Both nullable so existing rows are unaffected.
ALTER TABLE ai_template ADD COLUMN IF NOT EXISTS body     text;
ALTER TABLE ai_template ADD COLUMN IF NOT EXISTS category varchar(128);
