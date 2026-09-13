-- 教学中心主实体都继承 BaseEntity。补齐 Copilot 首版建表脚本遗漏的审计列，
-- 使生产环境 ddl-auto=validate 能够严格校验并正常启动。
ALTER TABLE edu_courseware
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;

ALTER TABLE edu_teaching_material
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;

ALTER TABLE edu_preparation
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128);

ALTER TABLE edu_question_bank
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;

ALTER TABLE edu_question
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;

ALTER TABLE edu_knowledge_point
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;

ALTER TABLE edu_error_book
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;

ALTER TABLE edu_research_group
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;

ALTER TABLE edu_research_activity
    ADD COLUMN IF NOT EXISTS last_update_by varchar(128),
    ADD COLUMN IF NOT EXISTS last_update_time timestamp;
