-- Phone number for SMS notification delivery. Nullable: not every user has one,
-- and SMS to a user without a phone is marked FAILED rather than sent.
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS phone varchar(32);
