-- 逐题录分和考试排期使用不同状态机。只有确认后的成绩才能发布并产生错题事实。
ALTER TABLE edu_exam_session
  ADD COLUMN IF NOT EXISTS score_status varchar(24) NOT NULL DEFAULT 'DRAFT';

ALTER TABLE edu_exam_session
  ADD COLUMN IF NOT EXISTS scores_confirmed_at timestamp;

ALTER TABLE edu_exam_session
  ADD COLUMN IF NOT EXISTS scores_published_at timestamp;

-- 状态确认和发布可能由多个浏览器请求同时触发，版本号用于阻止并发覆盖。
ALTER TABLE edu_exam_session
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_edu_exam_session_score_status
  ON edu_exam_session(score_status);
