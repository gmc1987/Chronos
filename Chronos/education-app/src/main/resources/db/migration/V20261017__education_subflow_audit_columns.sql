-- Complete lowercase columns required by unquoted Hibernate field mappings.
ALTER TABLE edu_preparation_member
  ADD COLUMN IF NOT EXISTS invitedby varchar(255),
  ADD COLUMN IF NOT EXISTS respondedat timestamptz,
  ADD COLUMN IF NOT EXISTS responsecomment text;

ALTER TABLE edu_courseware_version
  ADD COLUMN IF NOT EXISTS boundat timestamptz,
  ADD COLUMN IF NOT EXISTS publishedat timestamptz;

ALTER TABLE edu_teaching_material_version
  ADD COLUMN IF NOT EXISTS boundat timestamptz,
  ADD COLUMN IF NOT EXISTS publishedat timestamptz;
