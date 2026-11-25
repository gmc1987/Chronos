ALTER TABLE edu_course_offering DROP CONSTRAINT ck_edu_offering_students;

ALTER TABLE edu_course_offering ADD CONSTRAINT ck_edu_offering_students
    CHECK (student_count > 0 OR (status = 'TEACHING_RESOURCE_ONLY' AND student_count = 0));
