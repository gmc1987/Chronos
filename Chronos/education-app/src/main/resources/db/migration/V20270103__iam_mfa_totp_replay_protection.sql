-- Consume each accepted TOTP time step at most once, including concurrent requests.
ALTER TABLE iam_mfa_factor
  ADD COLUMN IF NOT EXISTS last_used_time_step bigint;
