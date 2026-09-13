-- Hibernate uses StandardNamingStrategy for these legacy subflow entities.
-- Keep the explicitly mapped snake_case columns and add the remaining
-- field-name columns required by the entity mappings.
ALTER TABLE edu_preparation_comment
  ADD COLUMN IF NOT EXISTS "updatedAt" timestamptz;

ALTER TABLE edu_preparation_member
  ADD COLUMN IF NOT EXISTS "invitedAt" timestamptz;

ALTER TABLE edu_courseware_version
  ADD COLUMN IF NOT EXISTS "archivedAt" timestamptz;

ALTER TABLE edu_teaching_material_version
  ADD COLUMN IF NOT EXISTS "archivedAt" timestamptz;
