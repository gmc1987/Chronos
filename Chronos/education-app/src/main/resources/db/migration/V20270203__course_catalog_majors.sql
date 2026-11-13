CREATE TABLE edu_course_catalog_major (
    course_id varchar(64) NOT NULL REFERENCES edu_course_catalog(id) ON DELETE CASCADE,
    major_id varchar(64) NOT NULL REFERENCES edu_major(id) ON DELETE CASCADE,
    PRIMARY KEY (course_id, major_id)
);

INSERT INTO edu_course_catalog_major (course_id, major_id)
SELECT DISTINCT item.course_id, program.major_id
FROM edu_academic_program_course item
JOIN edu_academic_program program ON program.id = item.program_id
JOIN edu_course_catalog course ON course.id = item.course_id
JOIN edu_major major ON major.id = program.major_id
WHERE program.status IN ('PUBLISHED', 'ARCHIVED')
  AND (course.organization_id IS NULL OR course.organization_id = program.school_id)
  AND (major.organization_id IS NULL OR major.organization_id = program.school_id)
  AND course.course_category <> 'GENERAL';
