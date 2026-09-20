-- Complete the preparation session production contract without rewriting ran migrations.
ALTER TABLE edu_preparation
  ADD COLUMN IF NOT EXISTS location varchar(200);
