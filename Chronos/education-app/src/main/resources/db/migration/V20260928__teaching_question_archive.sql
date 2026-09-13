-- 题目领域列表和归档操作均依赖 archived 字段。
ALTER TABLE edu_question
    ADD COLUMN IF NOT EXISTS archived boolean NOT NULL DEFAULT false;
