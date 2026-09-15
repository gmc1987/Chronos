ALTER TABLE edu_preparation
  ADD COLUMN IF NOT EXISTS scheduled_at timestamp,
  ADD COLUMN IF NOT EXISTS agenda text;
