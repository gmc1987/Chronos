-- Complete the exact-name columns required by CoursewareVersion's
-- unannotated boundAt and publishedAt fields.
ALTER TABLE edu_courseware_version
    ADD COLUMN IF NOT EXISTS "boundAt" timestamptz;

ALTER TABLE edu_courseware_version
    ADD COLUMN IF NOT EXISTS "publishedAt" timestamptz;
