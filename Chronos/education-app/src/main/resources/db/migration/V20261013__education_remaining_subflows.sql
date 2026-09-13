-- Final increment for the remaining teaching-center workflow invariants.
-- Event ids are the producer idempotency key; source_item_id remains the
-- business aggregation key and therefore is intentionally not unique.
ALTER TABLE edu_error_item
  ADD COLUMN IF NOT EXISTS event_id varchar(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_error_item_event_id
  ON edu_error_item(event_id) WHERE event_id IS NOT NULL;

-- A knowledge point with downstream question references must be retained for
-- historical snapshots.  The service rejects disabling those rows; this
-- index keeps the protection check bounded on production-sized banks.
CREATE INDEX IF NOT EXISTS idx_question_knowledge_point_kp
  ON edu_question_knowledge_point(knowledge_point_id);

