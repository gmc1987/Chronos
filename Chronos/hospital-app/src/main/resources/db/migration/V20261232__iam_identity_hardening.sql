-- Additive IAM hardening. Do not edit previously executed migrations.
ALTER TABLE iam_mfa_factor
  ADD COLUMN IF NOT EXISTS recovery_code_hashes text NOT NULL DEFAULT '';

ALTER TABLE iam_temporary_grant
  ADD COLUMN IF NOT EXISTS requested_by varchar(128),
  ADD COLUMN IF NOT EXISTS second_approved_by varchar(128);

UPDATE iam_temporary_grant
   SET requested_by = COALESCE(requested_by, create_by)
 WHERE requested_by IS NULL;

ALTER TABLE iam_temporary_grant
  ALTER COLUMN requested_by SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_iam_external_identity_user
  ON iam_external_identity(user_id);
