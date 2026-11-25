ALTER TABLE edu_parent_profile ADD COLUMN school_id varchar(64);

-- A legacy household with children in several schools remains an existing cross-school record.
WITH ownership AS (
    SELECT guardian.parent_id, min(CASE WHEN org.organization_type = 'SCHOOL' THEN org.id ELSE org.parent_org_id END) AS school_id
    FROM edu_student_guardian guardian
    JOIN edu_student_profile student ON student.id = guardian.student_id
    JOIN edu_administrative_class classroom ON classroom.id = student.administrative_class_id
    JOIN t_organization org ON org.id = classroom.campus_id
    GROUP BY guardian.parent_id
    HAVING count(DISTINCT CASE WHEN org.organization_type = 'SCHOOL' THEN org.id ELSE org.parent_org_id END) = 1
)
UPDATE edu_parent_profile parent
SET school_id = ownership.school_id
FROM ownership WHERE ownership.parent_id = parent.id;

CREATE INDEX idx_edu_parent_profile_school ON edu_parent_profile(school_id, parent_no);
