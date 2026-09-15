-- 四个考试子菜单分别进入独立业务页面，避免路由复用页签。
UPDATE t_menu
SET path = '/admin/education/exam/rooms'
WHERE id = 'e44dd6b4-4a77-4181-8b82-96a7fb8862f3';

UPDATE t_menu
SET path = '/admin/education/exam/paper-analysis'
WHERE id = '27f9e435-6420-43dc-9986-8e80ad15249f';

-- 逐题得分是分析的事实来源；题目结构冻结在具体考试场次下。
CREATE TABLE IF NOT EXISTS edu_exam_paper_item (
    id varchar(64) PRIMARY KEY,
    session_id varchar(64) NOT NULL REFERENCES edu_exam_session(id),
    question_no varchar(32) NOT NULL,
    title varchar(200) NOT NULL,
    max_score numeric(8, 2) NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_exam_paper_question UNIQUE (session_id, question_no),
    CONSTRAINT ck_edu_exam_paper_max_score CHECK (max_score > 0)
);

CREATE TABLE IF NOT EXISTS edu_exam_item_score (
    id varchar(64) PRIMARY KEY,
    item_id varchar(64) NOT NULL REFERENCES edu_exam_paper_item(id),
    candidate_id varchar(64) NOT NULL REFERENCES edu_exam_candidate(id),
    score numeric(8, 2) NOT NULL,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_exam_item_candidate UNIQUE (item_id, candidate_id),
    CONSTRAINT ck_edu_exam_item_score_nonnegative CHECK (score >= 0)
);

CREATE INDEX IF NOT EXISTS idx_edu_exam_paper_item_session
    ON edu_exam_paper_item(session_id);
CREATE INDEX IF NOT EXISTS idx_edu_exam_item_score_candidate
    ON edu_exam_item_score(candidate_id);

INSERT INTO t_permission (
    id, create_by, create_time, permission_code, permission_name,
    permission_type, action_type, built_in, status, menu_id)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       'education:exam:paper-analysis:manage', '维护试卷题目与逐题评分',
       'MENU_ACTION', 'MANAGE', true, 1,
       '27f9e435-6420-43dc-9986-8e80ad15249f'
WHERE NOT EXISTS (
    SELECT 1 FROM t_permission
    WHERE permission_code = 'education:exam:paper-analysis:manage'
);

-- 已获考试计划管理权的角色继承试卷分析维护权；SUPER_ADMIN 用于初始化环境。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE permission.permission_code = 'education:exam:paper-analysis:manage'
  AND (
      role.role_code = 'SUPER_ADMIN'
      OR EXISTS (
          SELECT 1
          FROM t_role_permission existing_grant
          JOIN t_permission existing_permission
            ON existing_permission.id = existing_grant.permission_id
          WHERE existing_grant.role_id = role.id
            AND existing_permission.permission_code = 'education:exam:plan:manage'
      )
  )
ON CONFLICT DO NOTHING;
