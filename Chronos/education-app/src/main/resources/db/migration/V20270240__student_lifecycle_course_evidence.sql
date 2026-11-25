CREATE TABLE edu_student_course_evidence (
    school_id varchar(64) NOT NULL REFERENCES t_organization(id),
    student_id varchar(64) NOT NULL REFERENCES edu_student_profile(id),
    course_id varchar(64) NOT NULL REFERENCES edu_course_catalog(id),
    reference varchar(255) NOT NULL,
    passed boolean NOT NULL,
    updated_by varchar(128) NOT NULL,
    updated_at timestamp NOT NULL DEFAULT current_timestamp,
    PRIMARY KEY (school_id,student_id,course_id)
);

-- Enrollment-only rows describe sections offered in the external academic system.
ALTER TABLE edu_course_offering ALTER COLUMN teacher_id DROP NOT NULL;
ALTER TABLE edu_course_offering ADD CONSTRAINT ck_offering_requires_teacher
    CHECK (status = 'ENROLLMENT_ONLY' OR teacher_id IS NOT NULL);

ALTER TABLE edu_course_offering DROP CONSTRAINT ck_edu_offering_students;
ALTER TABLE edu_course_offering ADD CONSTRAINT ck_edu_offering_students
    CHECK (student_count > 0 OR (status IN ('TEACHING_RESOURCE_ONLY','ENROLLMENT_ONLY') AND student_count = 0));
