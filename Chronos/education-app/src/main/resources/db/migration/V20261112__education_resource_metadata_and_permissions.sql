-- Complete server-owned resource metadata and keep courseware/material contracts distinct.
ALTER TABLE edu_courseware
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS resource_category varchar(32),
  ADD COLUMN IF NOT EXISTS license_code varchar(32);
ALTER TABLE edu_teaching_material
  ADD COLUMN IF NOT EXISTS description text,
  ADD COLUMN IF NOT EXISTS resource_category varchar(32),
  ADD COLUMN IF NOT EXISTS license_code varchar(32);
ALTER TABLE edu_courseware_version
  ADD COLUMN IF NOT EXISTS file_name varchar(255),
  ADD COLUMN IF NOT EXISTS mime_type varchar(128),
  ADD COLUMN IF NOT EXISTS file_size bigint,
  ADD COLUMN IF NOT EXISTS checksum_sha256 varchar(64),
  ADD COLUMN IF NOT EXISTS uploaded_at timestamp;
ALTER TABLE edu_teaching_material_version
  ADD COLUMN IF NOT EXISTS file_name varchar(255),
  ADD COLUMN IF NOT EXISTS mime_type varchar(128),
  ADD COLUMN IF NOT EXISTS file_size bigint,
  ADD COLUMN IF NOT EXISTS checksum_sha256 varchar(64),
  ADD COLUMN IF NOT EXISTS uploaded_at timestamp;
