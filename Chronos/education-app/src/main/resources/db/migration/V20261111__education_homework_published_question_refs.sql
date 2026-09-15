ALTER TABLE edu_homework_assignment
  ADD COLUMN IF NOT EXISTS question_version_refs_json text NOT NULL DEFAULT '[]';
