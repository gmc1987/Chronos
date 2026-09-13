-- CoursewareVersion uses the standard naming strategy, so its unannotated
-- archivedAt field requires the exact camel-case column name.
ALTER TABLE edu_courseware_version
    ADD COLUMN IF NOT EXISTS "archivedAt" timestamptz;
