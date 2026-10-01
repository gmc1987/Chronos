ALTER TABLE edu_research_activity_member
  ADD COLUMN IF NOT EXISTS invitation_status varchar(24) NOT NULL DEFAULT 'INVITED';
ALTER TABLE edu_research_activity_member
  ADD COLUMN IF NOT EXISTS attendance_updated_by varchar(64);
ALTER TABLE edu_error_item
  ADD COLUMN IF NOT EXISTS question_version_id varchar(64);
ALTER TABLE edu_error_item
  ADD COLUMN IF NOT EXISTS occurred_at timestamp;
