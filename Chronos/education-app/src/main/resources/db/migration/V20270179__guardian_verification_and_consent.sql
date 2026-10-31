-- 家校生产治理：监护关系必须具备核验、限制和成年人授权状态，避免仅凭绑定关系访问学生信息。
ALTER TABLE edu_student_guardian
  ADD COLUMN IF NOT EXISTS verification_status varchar(24) NOT NULL DEFAULT 'VERIFIED',
  ADD COLUMN IF NOT EXISTS verification_note varchar(1000),
  ADD COLUMN IF NOT EXISTS verified_by varchar(128),
  ADD COLUMN IF NOT EXISTS verified_at timestamp,
  ADD COLUMN IF NOT EXISTS restriction_level varchar(24) NOT NULL DEFAULT 'NONE',
  ADD COLUMN IF NOT EXISTS restriction_reason varchar(1000),
  ADD COLUMN IF NOT EXISTS restriction_from date,
  ADD COLUMN IF NOT EXISTS restriction_to date,
  ADD COLUMN IF NOT EXISTS student_consent_status varchar(24) NOT NULL DEFAULT 'NOT_REQUIRED',
  ADD COLUMN IF NOT EXISTS student_consented_at timestamp,
  ADD COLUMN IF NOT EXISTS consent_expires_at timestamp,
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;

UPDATE edu_student_guardian
SET verified_by = COALESCE(verified_by, 'SYSTEM_MIGRATION'),
    verified_at = COALESCE(verified_at, create_time)
WHERE verification_status = 'VERIFIED';

ALTER TABLE edu_student_guardian DROP CONSTRAINT IF EXISTS ck_edu_guardian_verification_status;
ALTER TABLE edu_student_guardian ADD CONSTRAINT ck_edu_guardian_verification_status
  CHECK (verification_status IN ('PENDING','VERIFIED','REJECTED','REVOKED'));
ALTER TABLE edu_student_guardian DROP CONSTRAINT IF EXISTS ck_edu_guardian_restriction_level;
ALTER TABLE edu_student_guardian ADD CONSTRAINT ck_edu_guardian_restriction_level
  CHECK (restriction_level IN ('NONE','CONTACT_ONLY','NO_ACCESS'));
ALTER TABLE edu_student_guardian DROP CONSTRAINT IF EXISTS ck_edu_guardian_consent_status;
ALTER TABLE edu_student_guardian ADD CONSTRAINT ck_edu_guardian_consent_status
  CHECK (student_consent_status IN ('NOT_REQUIRED','PENDING','GRANTED','DENIED','REVOKED'));
ALTER TABLE edu_student_guardian DROP CONSTRAINT IF EXISTS ck_edu_guardian_restriction_dates;
ALTER TABLE edu_student_guardian ADD CONSTRAINT ck_edu_guardian_restriction_dates
  CHECK (restriction_to IS NULL OR restriction_from IS NULL OR restriction_to >= restriction_from);

CREATE INDEX IF NOT EXISTS idx_edu_guardian_access
  ON edu_student_guardian(parent_id, verification_status, restriction_level, student_consent_status);
