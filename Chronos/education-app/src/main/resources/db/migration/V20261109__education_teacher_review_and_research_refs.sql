ALTER TABLE edu_research_activity
  ADD COLUMN IF NOT EXISTS course_id varchar(64),
  ADD COLUMN IF NOT EXISTS topic_id varchar(64);

CREATE TABLE IF NOT EXISTS edu_error_review (
  id varchar(64) PRIMARY KEY,
  error_item_id varchar(64) NOT NULL,
  reviewer_id varchar(64) NOT NULL,
  status varchar(24) NOT NULL,
  note text,
  reviewed_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_error_review_item_time
  ON edu_error_review(error_item_id, reviewed_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS uq_error_source_item
  ON edu_error_item(book_id, source_type, source_item_id)
  WHERE source_item_id IS NOT NULL;
