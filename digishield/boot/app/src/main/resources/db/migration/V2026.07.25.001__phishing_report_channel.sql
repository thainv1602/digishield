-- Channel the phishing report was submitted from (email | sms). Nullable:
-- older reports predate the field, and the SOC inbox tolerates a missing value.
ALTER TABLE phishing_report ADD COLUMN IF NOT EXISTS channel varchar(20);
