-- Preserve how a teaching resource was produced without coupling it to one
-- preparation session. Values include UPLOAD, GENERATED and COPIED.
ALTER TABLE edu_courseware
  ADD COLUMN IF NOT EXISTS source_type varchar(32);
ALTER TABLE edu_teaching_material
  ADD COLUMN IF NOT EXISTS source_type varchar(32);
