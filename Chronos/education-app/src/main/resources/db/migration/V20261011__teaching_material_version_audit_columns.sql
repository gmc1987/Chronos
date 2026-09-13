-- Complete fields declared by TeachingMaterialVersion but omitted by the
-- teaching-center migration.
ALTER TABLE edu_teaching_material_version
    ADD COLUMN IF NOT EXISTS status varchar(24) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE edu_teaching_material_version
    ADD COLUMN IF NOT EXISTS "boundAt" timestamptz;

ALTER TABLE edu_teaching_material_version
    ADD COLUMN IF NOT EXISTS "publishedAt" timestamptz;

ALTER TABLE edu_teaching_material_version
    ADD COLUMN IF NOT EXISTS "archivedAt" timestamptz;

ALTER TABLE edu_teaching_material_version
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128);

ALTER TABLE edu_teaching_material_version
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;
