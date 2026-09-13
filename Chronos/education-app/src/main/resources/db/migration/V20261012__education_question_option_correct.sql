-- Preserve the answer flag supplied by the question author. Older databases
-- created options before this field existed, so existing options remain
-- incorrect until explicitly edited.
ALTER TABLE edu_question_option
  ADD COLUMN IF NOT EXISTS correct boolean NOT NULL DEFAULT false;
