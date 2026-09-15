ALTER TABLE edu_research_activity_member
  ADD COLUMN IF NOT EXISTS invitation_status varchar(24) NOT NULL DEFAULT 'INVITED';
ALTER TABLE edu_research_activity_member
  ADD COLUMN IF NOT EXISTS attendance_updated_by varchar(64);
