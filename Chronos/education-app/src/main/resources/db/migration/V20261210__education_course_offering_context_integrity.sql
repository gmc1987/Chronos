-- Close the CourseOffering context without rewriting historical data.
-- NOT VALID preserves existing rows while enforcing all future writes.
ALTER TABLE edu_course_offering
  ADD CONSTRAINT fk_edu_offering_semester_code
  FOREIGN KEY (semester_code) REFERENCES edu_academic_term(term_code)
  NOT VALID;

ALTER TABLE edu_schedule_entry
  ADD CONSTRAINT fk_edu_schedule_semester_code
  FOREIGN KEY (semester_code) REFERENCES edu_academic_term(term_code)
  NOT VALID;

CREATE INDEX IF NOT EXISTS idx_edu_offering_semester_campus
  ON edu_course_offering(semester_code, campus_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_offering_semester_teacher
  ON edu_course_offering(semester_code, teacher_id, status);
CREATE INDEX IF NOT EXISTS idx_edu_assignment_term_teacher
  ON edu_teacher_teaching_assignment(academic_term_id, teacher_id, enabled);
CREATE INDEX IF NOT EXISTS idx_edu_schedule_published_semester
  ON edu_schedule_plan_version(semester_code, status, version_no DESC);
