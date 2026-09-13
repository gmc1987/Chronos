-- TeachingCenterResource declares versionNo, which was omitted from the
-- teaching-center table definition.
ALTER TABLE edu_teaching_center_resource
    ADD COLUMN IF NOT EXISTS version_no integer NOT NULL DEFAULT 1;
