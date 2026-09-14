-- Manual error records keep the selected question/knowledge context and
-- the teacher's normalized reason instead of losing it in free-form notes.
ALTER TABLE edu_error_item
  ADD COLUMN IF NOT EXISTS knowledge_point_id varchar(64),
  ADD COLUMN IF NOT EXISTS error_reason varchar(32);

CREATE INDEX IF NOT EXISTS idx_error_item_question_point
  ON edu_error_item(question_id, knowledge_point_id);
