-- Research invitations originally stored IAM employee IDs, while education
-- access checks use teacher profile IDs. Preserve the records and re-key them.
UPDATE edu_research_group research_group
SET leader_teacher_id = teacher.id
FROM edu_teacher_profile teacher
WHERE research_group.leader_teacher_id = teacher.employee_id;

UPDATE edu_research_group_member member
SET teacher_id = teacher.id
FROM edu_teacher_profile teacher
WHERE member.teacher_id = teacher.employee_id;

UPDATE edu_research_activity_member member
SET teacher_id = teacher.id
FROM edu_teacher_profile teacher
WHERE member.teacher_id = teacher.employee_id;

-- Replace the old placeholder only when the leader's assignments identify
-- exactly one school. Ambiguous historical groups need explicit correction.
WITH leader_schools AS (
    SELECT research_group.id,
           MIN(CASE WHEN organization.organization_type = 'SCHOOL'
                    THEN organization.id ELSE organization.parent_org_id END) AS school_id
    FROM edu_research_group research_group
    JOIN edu_course_offering offering ON offering.teacher_id = research_group.leader_teacher_id
    JOIN t_organization organization ON organization.id = offering.campus_id
    WHERE research_group.school_id = 'CURRENT'
    GROUP BY research_group.id
    HAVING COUNT(DISTINCT CASE WHEN organization.organization_type = 'SCHOOL'
                               THEN organization.id ELSE organization.parent_org_id END) = 1
)
UPDATE edu_research_group research_group
SET school_id = leader_schools.school_id
FROM leader_schools
WHERE research_group.id = leader_schools.id;
