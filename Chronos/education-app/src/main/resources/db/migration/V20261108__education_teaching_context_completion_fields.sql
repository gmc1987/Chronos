-- Complete the offering-scoped teaching production contract.
ALTER TABLE edu_teaching_plan_item
  ADD COLUMN IF NOT EXISTS knowledge_unit varchar(200),
  ADD COLUMN IF NOT EXISTS expected_date date,
  ADD COLUMN IF NOT EXISTS completion_status varchar(24) NOT NULL DEFAULT 'NOT_STARTED';

ALTER TABLE edu_preparation
  ADD COLUMN IF NOT EXISTS objective text,
  ADD COLUMN IF NOT EXISTS key_points text,
  ADD COLUMN IF NOT EXISTS difficult_points text,
  ADD COLUMN IF NOT EXISTS discussion text;

CREATE INDEX IF NOT EXISTS idx_teaching_plan_item_completion
  ON edu_teaching_plan_item(plan_id, completion_status, expected_date);
