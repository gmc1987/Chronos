-- 将课件/教学材料与备课、教案和教学计划项建立可追踪关联。
ALTER TABLE edu_courseware
  ADD COLUMN IF NOT EXISTS preparation_id varchar(64),
  ADD COLUMN IF NOT EXISTS lesson_plan_id varchar(64),
  ADD COLUMN IF NOT EXISTS plan_item_id varchar(64);
ALTER TABLE edu_teaching_material
  ADD COLUMN IF NOT EXISTS preparation_id varchar(64),
  ADD COLUMN IF NOT EXISTS lesson_plan_id varchar(64),
  ADD COLUMN IF NOT EXISTS plan_item_id varchar(64);
CREATE INDEX IF NOT EXISTS idx_courseware_source_links
  ON edu_courseware(preparation_id, lesson_plan_id, plan_item_id);
CREATE INDEX IF NOT EXISTS idx_teaching_material_source_links
  ON edu_teaching_material(preparation_id, lesson_plan_id, plan_item_id);
