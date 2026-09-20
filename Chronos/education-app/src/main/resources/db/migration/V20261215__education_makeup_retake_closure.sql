CREATE TABLE IF NOT EXISTS edu_makeup_retake_record (
 id varchar(64) PRIMARY KEY, create_by varchar(128) NOT NULL, create_time timestamp NOT NULL,
 last_update_by varchar(128), last_update_time timestamp, source_course_grade_id varchar(64) NOT NULL,
 source_gradebook_id varchar(64) NOT NULL, student_id varchar(64) NOT NULL, offering_id varchar(64) NOT NULL,
 exam_session_id varchar(64) NOT NULL, exam_candidate_id varchar(64) NOT NULL, record_type varchar(16) NOT NULL,
 result_score numeric(8,2) NOT NULL, max_score numeric(8,2) NOT NULL, original_score numeric(8,2) NOT NULL,
 effective_score numeric(8,2), strategy varchar(24) NOT NULL, status varchar(24) NOT NULL DEFAULT 'DRAFT',
 source_snapshot_hash varchar(64) NOT NULL, published_snapshot_json text, submitted_by varchar(128),
 approved_by varchar(128), published_by varchar(128), submitted_at timestamp, approved_at timestamp,
 published_at timestamp, row_version bigint NOT NULL DEFAULT 0,
 CONSTRAINT uk_edu_makeup_retake_source UNIQUE(exam_session_id,exam_candidate_id,record_type),
 CONSTRAINT ck_edu_makeup_retake_type CHECK(record_type IN('MAKEUP','RETAKE')),
 CONSTRAINT ck_edu_makeup_retake_status CHECK(status IN('DRAFT','SUBMITTED','APPROVED','REJECTED','PUBLISHED')),
 CONSTRAINT ck_edu_makeup_retake_score CHECK(result_score >= 0 AND max_score > 0 AND original_score >= 0)
);
CREATE INDEX IF NOT EXISTS idx_edu_makeup_retake_student ON edu_makeup_retake_record(student_id,status,published_at);

INSERT INTO t_permission(id,create_by,create_time,permission_code,permission_name,permission_type,action_type,built_in,status,resource_type,scope_type,description)
SELECT gen_random_uuid()::text,'SYSTEM',CURRENT_TIMESTAMP,x.code,x.name,'MENU_ACTION',x.action,true,1,'EDUCATION_SCORE','ROLE',x.description
FROM (VALUES
 ('education:score:makeup-retake:manage','创建补考重修记录','CREATE','基于已发布考试成绩创建补考/重修记录'),
 ('education:score:makeup-retake:submit','提交补考重修审核','SUBMIT','提交补考/重修记录审核'),
 ('education:score:makeup-retake:approve','审批补考重修记录','APPROVE','审批补考/重修记录'),
 ('education:score:makeup-retake:publish','发布补考重修结果','PUBLISH','发布补考/重修结果快照')
) x(code,name,action,description)
ON CONFLICT(permission_code) DO UPDATE SET permission_name=EXCLUDED.permission_name,action_type=EXCLUDED.action_type,status=1;
INSERT INTO t_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM t_role r CROSS JOIN t_permission p
WHERE r.role_code IN('ROLE_PLATFORM_ADMIN','SUPER_ADMIN','EDU_ADMIN')
AND p.permission_code IN('education:score:makeup-retake:manage','education:score:makeup-retake:submit',
 'education:score:makeup-retake:approve','education:score:makeup-retake:publish')
ON CONFLICT DO NOTHING;
