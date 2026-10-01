-- Complete the generic integration job contract introduced after the shared
-- integration foundation migration.
ALTER TABLE int_sync_job
  ADD COLUMN IF NOT EXISTS object_type varchar(64) NOT NULL DEFAULT 'DEFAULT',
  ADD COLUMN IF NOT EXISTS mapping_version_no integer NOT NULL DEFAULT 1;
