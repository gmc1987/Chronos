-- Keep production resources rooted in CourseOffering without changing legacy data.
-- The application validates cross-resource references against the same offering;
-- these indexes make those validations and offering views efficient.
CREATE INDEX IF NOT EXISTS idx_edu_teaching_plan_offering_active
  ON edu_teaching_plan(offering_id, archived, create_time);
CREATE INDEX IF NOT EXISTS idx_edu_teaching_plan_item_plan
  ON edu_teaching_plan_item(plan_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_edu_lesson_plan_offering_active
  ON edu_lesson_plan(offering_id, archived, create_time);
CREATE INDEX IF NOT EXISTS idx_edu_preparation_offering_active
  ON edu_preparation(offering_id, archived, create_time);
CREATE INDEX IF NOT EXISTS idx_edu_homework_assignment_references
  ON edu_homework_assignment(offering_id, teaching_plan_item_id, preparation_id, lesson_plan_id);
