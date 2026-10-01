-- 作业生命周期、学生提交、教师评分和成绩发布均可能发生并发写入。
-- 由 Hibernate @Version 使用这些字段检测陈旧写入，避免后到请求静默覆盖先到请求。
ALTER TABLE edu_homework_assignment
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;

ALTER TABLE edu_homework_submission
  ADD COLUMN IF NOT EXISTS row_version bigint NOT NULL DEFAULT 0;
