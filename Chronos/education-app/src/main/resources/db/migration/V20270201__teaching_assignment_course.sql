ALTER TABLE edu_teacher_teaching_assignment
    ADD COLUMN course_id varchar(64) REFERENCES edu_course_catalog(id);
