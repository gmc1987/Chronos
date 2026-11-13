-- Keep existing catalog records shared because they may already be referenced by multiple schools.
ALTER TABLE edu_subject ADD COLUMN organization_id varchar(64);
ALTER TABLE edu_course_catalog ADD COLUMN organization_id varchar(64);
ALTER TABLE edu_major ADD COLUMN organization_id varchar(64);

CREATE INDEX idx_edu_subject_org_code ON edu_subject (organization_id, subject_code);
CREATE INDEX idx_edu_course_catalog_org_code ON edu_course_catalog (organization_id, course_code);
CREATE INDEX idx_edu_major_org_code ON edu_major (organization_id, major_code);
