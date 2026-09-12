-- 教学中心领域表。文件字段只保存 platform-file 的 fileId，不保存二进制。
CREATE TABLE IF NOT EXISTS edu_teaching_center_resource (
 id varchar(64) PRIMARY KEY, resource_type varchar(32) NOT NULL, offering_id varchar(64),
 schedule_entry_id varchar(64), title varchar(200) NOT NULL, category varchar(64),
 status varchar(24) NOT NULL DEFAULT 'DRAFT', content text, file_id varchar(64),
 metadata_json text, archived boolean NOT NULL DEFAULT false,
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
 last_update_by varchar(128), last_update_time timestamp
);
CREATE INDEX IF NOT EXISTS idx_teaching_resource_type_offering
 ON edu_teaching_center_resource(resource_type, offering_id);

CREATE TABLE IF NOT EXISTS edu_teaching_plan (
 id varchar(64) PRIMARY KEY, offering_id varchar(64), name varchar(200) NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'DRAFT', subject varchar(128), grade varchar(64),
 archived boolean NOT NULL DEFAULT false, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
 last_update_by varchar(128), last_update_time timestamp
);
CREATE TABLE IF NOT EXISTS edu_teaching_plan_item (
 id varchar(64) PRIMARY KEY, plan_id varchar(64) NOT NULL, chapter_no integer,
 chapter_name varchar(200), lesson_hours integer, objectives text, key_points text,
 difficult_points text, sort_order integer NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS edu_teaching_plan_version (
 id varchar(64) PRIMARY KEY, plan_id varchar(64) NOT NULL, version_no integer NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'DRAFT', snapshot_json text, published_at timestamp,
 archived_at timestamp, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS edu_lesson_plan (
 id varchar(64) PRIMARY KEY, offering_id varchar(64) NOT NULL, schedule_entry_id varchar(64),
 title varchar(200) NOT NULL, status varchar(24) NOT NULL DEFAULT 'DRAFT',
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, last_update_by varchar(128), last_update_time timestamp,
 archived boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS edu_lesson_plan_version (
 id varchar(64) PRIMARY KEY, lesson_plan_id varchar(64) NOT NULL, version_no integer NOT NULL,
 content text, file_id varchar(64), status varchar(24) NOT NULL DEFAULT 'DRAFT',
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);
CREATE TABLE IF NOT EXISTS edu_lesson_plan_review (
 id varchar(64) PRIMARY KEY, lesson_plan_version_id varchar(64) NOT NULL,
 reviewer_id varchar(64) NOT NULL, decision varchar(24) NOT NULL, comment text,
 reviewed_at timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS edu_preparation (
 id varchar(64) PRIMARY KEY, offering_id varchar(64) NOT NULL, title varchar(200) NOT NULL,
 preparation_type varchar(16) NOT NULL, status varchar(24) NOT NULL DEFAULT 'DRAFT',
 conclusion text, archived boolean NOT NULL DEFAULT false, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, last_update_time timestamp
);
CREATE TABLE IF NOT EXISTS edu_preparation_member (
 id varchar(64) PRIMARY KEY, preparation_id varchar(64) NOT NULL, teacher_id varchar(64) NOT NULL,
 role varchar(32), joined_at timestamp
);
CREATE TABLE IF NOT EXISTS edu_preparation_material (
 id varchar(64) PRIMARY KEY, preparation_id varchar(64) NOT NULL, file_id varchar(64),
 title varchar(200) NOT NULL, metadata_json text
);
CREATE TABLE IF NOT EXISTS edu_preparation_comment (
 id varchar(64) PRIMARY KEY, preparation_id varchar(64) NOT NULL, author_id varchar(64) NOT NULL,
 content text NOT NULL, create_time timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS edu_courseware (
 id varchar(64) PRIMARY KEY, offering_id varchar(64) NOT NULL, title varchar(200) NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'DRAFT', share_scope varchar(32) NOT NULL DEFAULT 'PRIVATE',
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, archived boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS edu_courseware_version (
 id varchar(64) PRIMARY KEY, courseware_id varchar(64) NOT NULL, version_no integer NOT NULL,
 file_id varchar(64) NOT NULL, metadata_json text, status varchar(24) NOT NULL DEFAULT 'DRAFT',
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);
CREATE TABLE IF NOT EXISTS edu_teaching_material (
 id varchar(64) PRIMARY KEY, offering_id varchar(64) NOT NULL, title varchar(200) NOT NULL,
 material_type varchar(32), share_scope varchar(32) NOT NULL DEFAULT 'PRIVATE',
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, archived boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS edu_teaching_material_version (
 id varchar(64) PRIMARY KEY, material_id varchar(64) NOT NULL, version_no integer NOT NULL,
 file_id varchar(64) NOT NULL, metadata_json text, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS edu_question_bank (
 id varchar(64) PRIMARY KEY, offering_id varchar(64), name varchar(200) NOT NULL,
 subject varchar(128), status varchar(24) NOT NULL DEFAULT 'DRAFT', archived boolean NOT NULL DEFAULT false,
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);
CREATE TABLE IF NOT EXISTS edu_question (
 id varchar(64) PRIMARY KEY, bank_id varchar(64) NOT NULL, question_type varchar(24) NOT NULL,
 difficulty varchar(16), stem text NOT NULL, status varchar(24) NOT NULL DEFAULT 'DRAFT',
 answer text, analysis text, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);
CREATE TABLE IF NOT EXISTS edu_question_option (
 id varchar(64) PRIMARY KEY, question_id varchar(64) NOT NULL, option_key varchar(8) NOT NULL,
 option_text text NOT NULL, sort_order integer NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS edu_question_knowledge_point (
 question_id varchar(64) NOT NULL, knowledge_point_id varchar(64) NOT NULL,
 PRIMARY KEY(question_id, knowledge_point_id)
);

CREATE TABLE IF NOT EXISTS edu_knowledge_point (
 id varchar(64) PRIMARY KEY, parent_id varchar(64), subject_id varchar(64), course_id varchar(64),
 name varchar(200) NOT NULL, sort_order integer NOT NULL DEFAULT 0, enabled boolean NOT NULL DEFAULT true, archived boolean NOT NULL DEFAULT false,
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_knowledge_point_parent ON edu_knowledge_point(parent_id);

CREATE TABLE IF NOT EXISTS edu_error_book (
 id varchar(64) PRIMARY KEY, student_id varchar(64) NOT NULL, name varchar(200) NOT NULL,
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, archived boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS edu_error_item (
 id varchar(64) PRIMARY KEY, book_id varchar(64) NOT NULL, question_id varchar(64),
 source_ref varchar(64), source_type varchar(32), analysis text, status varchar(24) NOT NULL DEFAULT 'OPEN',
 create_time timestamp NOT NULL
);

CREATE TABLE IF NOT EXISTS edu_research_group (
 id varchar(64) PRIMARY KEY, name varchar(200) NOT NULL, subject_id varchar(64),
 status varchar(24) NOT NULL DEFAULT 'ACTIVE', archived boolean NOT NULL DEFAULT false, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL
);
CREATE TABLE IF NOT EXISTS edu_research_group_member (
 id varchar(64) PRIMARY KEY, group_id varchar(64) NOT NULL, teacher_id varchar(64) NOT NULL, role varchar(32)
);
CREATE TABLE IF NOT EXISTS edu_research_activity (
 id varchar(64) PRIMARY KEY, group_id varchar(64) NOT NULL, title varchar(200) NOT NULL,
 status varchar(24) NOT NULL DEFAULT 'DRAFT', activity_time timestamp, content text,
 create_by varchar(128) NOT NULL, create_time timestamp NOT NULL, archived boolean NOT NULL DEFAULT false
);
CREATE TABLE IF NOT EXISTS edu_research_material (
 id varchar(64) PRIMARY KEY, activity_id varchar(64) NOT NULL, file_id varchar(64), title varchar(200) NOT NULL
);
CREATE TABLE IF NOT EXISTS edu_research_activity_member (
 id varchar(64) PRIMARY KEY, activity_id varchar(64) NOT NULL, teacher_id varchar(64) NOT NULL, role varchar(32)
);
CREATE TABLE IF NOT EXISTS edu_research_result (
 id varchar(64) PRIMARY KEY, activity_id varchar(64) NOT NULL, title varchar(200) NOT NULL,
 content text, file_id varchar(64), status varchar(24) NOT NULL DEFAULT 'DRAFT'
);
CREATE INDEX IF NOT EXISTS idx_teaching_domain_offering ON edu_lesson_plan(offering_id);
CREATE INDEX IF NOT EXISTS idx_preparation_offering ON edu_preparation(offering_id);
CREATE INDEX IF NOT EXISTS idx_question_bank_offering ON edu_question_bank(offering_id);
CREATE INDEX IF NOT EXISTS idx_research_activity_group ON edu_research_activity(group_id);

-- 聚合子表的引用完整性与重复约束（本迁移首次部署时一并建立）。
ALTER TABLE edu_teaching_plan_item ADD CONSTRAINT fk_plan_item_plan
 FOREIGN KEY (plan_id) REFERENCES edu_teaching_plan(id);
ALTER TABLE edu_teaching_plan_version ADD CONSTRAINT fk_plan_version_plan
 FOREIGN KEY (plan_id) REFERENCES edu_teaching_plan(id);
ALTER TABLE edu_lesson_plan_version ADD CONSTRAINT fk_lesson_version_plan
 FOREIGN KEY (lesson_plan_id) REFERENCES edu_lesson_plan(id);
ALTER TABLE edu_lesson_plan_review ADD CONSTRAINT fk_lesson_review_version
 FOREIGN KEY (lesson_plan_version_id) REFERENCES edu_lesson_plan_version(id);
ALTER TABLE edu_preparation_member ADD CONSTRAINT fk_preparation_member
 FOREIGN KEY (preparation_id) REFERENCES edu_preparation(id);
ALTER TABLE edu_preparation_material ADD CONSTRAINT fk_preparation_material
 FOREIGN KEY (preparation_id) REFERENCES edu_preparation(id);
ALTER TABLE edu_preparation_comment ADD CONSTRAINT fk_preparation_comment
 FOREIGN KEY (preparation_id) REFERENCES edu_preparation(id);
ALTER TABLE edu_question ADD CONSTRAINT fk_question_bank
 FOREIGN KEY (bank_id) REFERENCES edu_question_bank(id);
ALTER TABLE edu_question_option ADD CONSTRAINT fk_question_option
 FOREIGN KEY (question_id) REFERENCES edu_question(id);
ALTER TABLE edu_question_knowledge_point ADD CONSTRAINT fk_question_knowledge
 FOREIGN KEY (question_id) REFERENCES edu_question(id);
ALTER TABLE edu_question_knowledge_point ADD CONSTRAINT fk_question_knowledge_point
 FOREIGN KEY (knowledge_point_id) REFERENCES edu_knowledge_point(id);
ALTER TABLE edu_knowledge_point ADD CONSTRAINT fk_knowledge_parent
 FOREIGN KEY (parent_id) REFERENCES edu_knowledge_point(id);
ALTER TABLE edu_error_item ADD CONSTRAINT fk_error_item_book
 FOREIGN KEY (book_id) REFERENCES edu_error_book(id);
ALTER TABLE edu_research_group_member ADD CONSTRAINT fk_research_member_group
 FOREIGN KEY (group_id) REFERENCES edu_research_group(id);
ALTER TABLE edu_research_activity ADD CONSTRAINT fk_research_activity_group
 FOREIGN KEY (group_id) REFERENCES edu_research_group(id);
ALTER TABLE edu_research_material ADD CONSTRAINT fk_research_material_activity
 FOREIGN KEY (activity_id) REFERENCES edu_research_activity(id);
ALTER TABLE edu_research_activity_member ADD CONSTRAINT fk_research_activity_member
 FOREIGN KEY (activity_id) REFERENCES edu_research_activity(id);
ALTER TABLE edu_research_result ADD CONSTRAINT fk_research_result_activity
 FOREIGN KEY (activity_id) REFERENCES edu_research_activity(id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_question_option_key ON edu_question_option(question_id, option_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_plan_version_no ON edu_teaching_plan_version(plan_id, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lesson_version_no ON edu_lesson_plan_version(lesson_plan_id, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_courseware_version_no ON edu_courseware_version(courseware_id, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_material_version_no ON edu_teaching_material_version(material_id, version_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_preparation_member_teacher ON edu_preparation_member(preparation_id, teacher_id);
