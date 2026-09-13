-- PostgreSQL folds unquoted StandardNamingStrategy field names to lowercase.
-- These columns complement the quoted legacy compatibility columns.
ALTER TABLE edu_preparation_comment
  ADD COLUMN IF NOT EXISTS updatedat timestamptz;

ALTER TABLE edu_preparation_member
  ADD COLUMN IF NOT EXISTS invitedat timestamptz;

ALTER TABLE edu_courseware_version
  ADD COLUMN IF NOT EXISTS archivedat timestamptz;

ALTER TABLE edu_teaching_material_version
  ADD COLUMN IF NOT EXISTS archivedat timestamptz;
