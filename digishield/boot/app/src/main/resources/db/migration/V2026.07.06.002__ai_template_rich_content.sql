-- Content Studio rich content: an HTML body format, an impersonated brand logo,
-- and simulated attachment metadata. All nullable / defaulted so existing rows
-- are unaffected.
ALTER TABLE ai_template ADD COLUMN IF NOT EXISTS body_format      varchar(8) NOT NULL DEFAULT 'TEXT';
ALTER TABLE ai_template ADD COLUMN IF NOT EXISTS logo_url         varchar(2048);
ALTER TABLE ai_template ADD COLUMN IF NOT EXISTS attachments_json text;
