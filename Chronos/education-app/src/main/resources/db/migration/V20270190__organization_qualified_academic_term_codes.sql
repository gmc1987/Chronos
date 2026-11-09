-- 学期对外使用“机构编码 + 校内学期编码”的稳定业务键。
-- 下游表仍保存一个不可歧义的 term_code/semester_code，避免同名学期跨机构串数。
ALTER TABLE edu_academic_term ADD COLUMN IF NOT EXISTS local_term_code varchar(64);
UPDATE edu_academic_term SET local_term_code = term_code WHERE local_term_code IS NULL;

ALTER TABLE edu_course_offering DROP CONSTRAINT IF EXISTS fk_edu_offering_semester_code;
ALTER TABLE edu_schedule_entry DROP CONSTRAINT IF EXISTS fk_edu_schedule_semester_code;

ALTER TABLE agent_run ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_academic_term ALTER COLUMN term_code TYPE varchar(128);
ALTER TABLE edu_classroom_reservation ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_classroom_unavailable_slot ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_course_enrollment ALTER COLUMN term_code TYPE varchar(128);
ALTER TABLE edu_course_offering ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_exam_plan ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_internship_placement ALTER COLUMN term_code TYPE varchar(128);
ALTER TABLE edu_schedule_candidate_plan ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_schedule_date_exception ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_schedule_entry ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_schedule_generation_job ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_schedule_plan_version ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_schedule_policy ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_scheduling_agent_proposal ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_student_accommodation ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_teacher_time_constraint ALTER COLUMN semester_code TYPE varchar(128);
ALTER TABLE edu_term_registration ALTER COLUMN term_code TYPE varchar(128);

UPDATE agent_run value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_classroom_reservation value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_classroom_unavailable_slot value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_course_enrollment value SET term_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.term_code = term.term_code;
UPDATE edu_course_offering value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_exam_plan value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_internship_placement value SET term_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.term_code = term.term_code;
UPDATE edu_schedule_candidate_plan value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_schedule_date_exception value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_schedule_entry value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_schedule_generation_job value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_schedule_plan_version value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_schedule_policy value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_scheduling_agent_proposal value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_student_accommodation value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_teacher_time_constraint value SET semester_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.semester_code = term.term_code;
UPDATE edu_term_registration value SET term_code = org.org_code || ':' || term.local_term_code
FROM edu_academic_term term JOIN t_organization org ON org.id = term.organization_id
WHERE value.term_code = term.term_code;

UPDATE edu_academic_term term
SET term_code = org.org_code || ':' || term.local_term_code
FROM t_organization org
WHERE org.id = term.organization_id
  AND term.term_code <> org.org_code || ':' || term.local_term_code;

ALTER TABLE edu_academic_term ALTER COLUMN local_term_code SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_edu_term_org_local_code
  ON edu_academic_term(organization_id, local_term_code);

ALTER TABLE edu_course_offering
  ADD CONSTRAINT fk_edu_offering_semester_code
  FOREIGN KEY (semester_code) REFERENCES edu_academic_term(term_code) NOT VALID;
ALTER TABLE edu_schedule_entry
  ADD CONSTRAINT fk_edu_schedule_semester_code
  FOREIGN KEY (semester_code) REFERENCES edu_academic_term(term_code) NOT VALID;
