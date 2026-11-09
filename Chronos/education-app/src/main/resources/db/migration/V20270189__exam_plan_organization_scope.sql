ALTER TABLE edu_exam_plan ADD COLUMN IF NOT EXISTS organization_id varchar(64);

UPDATE edu_exam_plan plan
   SET organization_id = term.organization_id
  FROM edu_academic_term term
 WHERE plan.semester_code = term.term_code
   AND (plan.organization_id IS NULL OR plan.organization_id = '');

UPDATE edu_exam_plan plan
   SET organization_id = fallback.organization_id
  FROM (
      SELECT campus_id AS organization_id
        FROM edu_administrative_class
       WHERE campus_id IS NOT NULL AND campus_id <> ''
       GROUP BY campus_id
       ORDER BY count(*) DESC, campus_id
       LIMIT 1
  ) fallback
 WHERE plan.organization_id IS NULL OR plan.organization_id = '';

ALTER TABLE edu_exam_plan ALTER COLUMN organization_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_edu_exam_plan_org_semester
    ON edu_exam_plan(organization_id, semester_code, start_date);
