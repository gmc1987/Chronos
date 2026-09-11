-- 智慧校园 MVP 可视化业务演示数据。
--
-- 目标：为通知公告和 Scheduling Agent 页面补充可直接查看的数据。
-- 约束：
-- 1. 所有主键现场生成，不使用 demo-xxx 伪主键；
-- 2. 外键全部从 ChronosEducation 现有有效数据中解析；
-- 3. 脚本可重复执行，不覆盖用户在页面上的后续修改；
-- 4. 任一必要基础数据缺失时立即报错并整体回滚。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('education-campus-mvp-business-demo-v1'));

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM t_admin_user
        WHERE username = 'admin'
          AND status = 1
    ) THEN
        RAISE EXCEPTION '缺少有效 admin 账号，无法生成通知公告演示数据';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM edu_academic_term
        WHERE current_term = true
          AND status = 'ACTIVE'
    ) THEN
        RAISE EXCEPTION '缺少当前有效学期，无法生成 Scheduling Agent 演示数据';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM edu_teacher_profile
        WHERE enabled = true
    ) THEN
        RAISE EXCEPTION '缺少有效教师档案，无法生成 Scheduling Agent 演示数据';
    END IF;
END
$$;

-- 永久公告：用于验证门户展示、置顶、必读和全员快照受众。
INSERT INTO msg_publication (
    id,
    create_by,
    create_time,
    last_update_by,
    last_update_time,
    approval_required,
    audience_mode,
    content,
    content_type,
    importance,
    lock_version,
    must_read,
    pinned,
    publication_type,
    published_at,
    sort_order,
    status,
    summary,
    title,
    version_no,
    archived
)
SELECT
    gen_random_uuid()::text,
    'admin',
    CURRENT_TIMESTAMP,
    'admin',
    CURRENT_TIMESTAMP,
    false,
    'SNAPSHOT',
    '<p>欢迎使用 Chronos AI Native 智慧校园综合平台。本公告用于验证门户通知公告、必读回执和受众快照能力。</p>',
    'RICH_TEXT',
    'HIGH',
    0,
    true,
    true,
    'ANNOUNCEMENT',
    CURRENT_TIMESTAMP,
    100,
    'PUBLISHED',
    '智慧校园 MVP 门户公告演示数据',
    '智慧校园平台启用公告（MVP演示）',
    1,
    false
WHERE NOT EXISTS (
    SELECT 1
    FROM msg_publication
    WHERE title = '智慧校园平台启用公告（MVP演示）'
);

-- 有效期通知：用于验证普通通知、有效期和未读状态。
INSERT INTO msg_publication (
    id,
    create_by,
    create_time,
    last_update_by,
    last_update_time,
    approval_required,
    audience_mode,
    content,
    content_type,
    expire_at,
    importance,
    lock_version,
    must_read,
    pinned,
    publication_type,
    published_at,
    sort_order,
    status,
    summary,
    title,
    version_no,
    archived
)
SELECT
    gen_random_uuid()::text,
    'admin',
    CURRENT_TIMESTAMP,
    'admin',
    CURRENT_TIMESTAMP,
    false,
    'SNAPSHOT',
    '<p>请教师和学生核对当前学期个人课表。如发现冲突，请通过调课流程提交申请。</p>',
    'RICH_TEXT',
    CURRENT_TIMESTAMP + INTERVAL '90 days',
    'NORMAL',
    0,
    false,
    false,
    'NOTICE',
    CURRENT_TIMESTAMP,
    50,
    'PUBLISHED',
    '用于验证通知有效期与门户未读状态',
    '当前学期课表核对通知（MVP演示）',
    1,
    false
WHERE NOT EXISTS (
    SELECT 1
    FROM msg_publication
    WHERE title = '当前学期课表核对通知（MVP演示）'
);

-- 两条发布内容均采用全员快照受众。
INSERT INTO msg_publication_audience (
    id,
    create_by,
    create_time,
    last_update_by,
    last_update_time,
    excluded,
    include_children,
    publication_id,
    subject_id,
    subject_type
)
SELECT
    gen_random_uuid()::text,
    'admin',
    CURRENT_TIMESTAMP,
    'admin',
    CURRENT_TIMESTAMP,
    false,
    false,
    publication.id,
    '*',
    'ALL'
FROM msg_publication publication
WHERE publication.title IN (
    '智慧校园平台启用公告（MVP演示）',
    '当前学期课表核对通知（MVP演示）'
)
  AND NOT EXISTS (
      SELECT 1
      FROM msg_publication_audience existing
      WHERE existing.publication_id = publication.id
        AND existing.subject_type = 'ALL'
        AND existing.subject_id = '*'
        AND existing.excluded = false
  );

-- 快照受众必须持久化收件人，否则门户查询不会把公告暴露给用户。
INSERT INTO msg_publication_recipient (
    id,
    create_by,
    create_time,
    last_update_by,
    last_update_time,
    publication_id,
    username
)
SELECT
    gen_random_uuid()::text,
    'admin',
    CURRENT_TIMESTAMP,
    'admin',
    CURRENT_TIMESTAMP,
    publication.id,
    app_user.username
FROM msg_publication publication
CROSS JOIN t_admin_user app_user
WHERE publication.title IN (
    '智慧校园平台启用公告（MVP演示）',
    '当前学期课表核对通知（MVP演示）'
)
  AND app_user.status = 1
  AND NOT EXISTS (
      SELECT 1
      FROM msg_publication_recipient existing
      WHERE existing.publication_id = publication.id
        AND existing.username = app_user.username
  );

-- 为管理员版本页补充初始快照。快照仅描述测试数据，不依赖固定主键。
INSERT INTO msg_publication_version (
    id,
    create_by,
    create_time,
    last_update_by,
    last_update_time,
    operation,
    publication_id,
    snapshot_json,
    version_no
)
SELECT
    gen_random_uuid()::text,
    'admin',
    CURRENT_TIMESTAMP,
    'admin',
    CURRENT_TIMESTAMP,
    'PUBLISH',
    publication.id,
    jsonb_build_object(
        'publication', jsonb_build_object(
            'publicationType', publication.publication_type,
            'contentType', publication.content_type,
            'title', publication.title,
            'summary', publication.summary,
            'content', publication.content,
            'importance', publication.importance,
            'pinned', publication.pinned,
            'sortOrder', publication.sort_order,
            'mustRead', publication.must_read,
            'audienceMode', publication.audience_mode,
            'expireAt', publication.expire_at
        ),
        'audiences', jsonb_build_array(
            jsonb_build_object(
                'subjectType', 'ALL',
                'subjectId', '*',
                'includeChildren', false,
                'excluded', false
            )
        )
    )::text,
    1
FROM msg_publication publication
WHERE publication.title IN (
    '智慧校园平台启用公告（MVP演示）',
    '当前学期课表核对通知（MVP演示）'
)
  AND NOT EXISTS (
      SELECT 1
      FROM msg_publication_version existing
      WHERE existing.publication_id = publication.id
        AND existing.version_no = 1
  );

-- 使用当前学期和真实教师档案生成一条待人工确认的 Agent 建议。
WITH current_term AS (
    SELECT term_code
    FROM edu_academic_term
    WHERE current_term = true
      AND status = 'ACTIVE'
    ORDER BY start_date DESC
    LIMIT 1
), target_teacher AS (
    SELECT id, teacher_name
    FROM edu_teacher_profile
    WHERE enabled = true
    ORDER BY teacher_no, id
    LIMIT 1
)
INSERT INTO edu_scheduling_agent_proposal (
    id,
    semester_code,
    request_text,
    teacher_id,
    teacher_name,
    day_of_week,
    period_no,
    constraint_type,
    reason,
    status,
    create_by,
    create_time,
    last_update_by,
    last_update_time
)
SELECT
    gen_random_uuid()::text,
    current_term.term_code,
    '请避免为' || target_teacher.teacher_name || '安排周五第8节课程（MVP演示）',
    target_teacher.id,
    target_teacher.teacher_name,
    5,
    8,
    'FORBIDDEN',
    '用于验证 Scheduling Agent 建议的人工确认或驳回闭环',
    'DRAFT',
    'admin',
    CURRENT_TIMESTAMP,
    'admin',
    CURRENT_TIMESTAMP
FROM current_term
CROSS JOIN target_teacher
WHERE NOT EXISTS (
    SELECT 1
    FROM edu_scheduling_agent_proposal existing
    WHERE existing.request_text =
        '请避免为' || target_teacher.teacher_name || '安排周五第8节课程（MVP演示）'
);

-- 为教务审批测试账号生成其数据范围内的建议，便于验证“可见但需人工确认”。
-- 优先从用户档案绑定解析教师，不通过姓名或固定 ID 猜测关联关系。
WITH current_term AS (
    SELECT term_code
    FROM edu_academic_term
    WHERE current_term = true
      AND status = 'ACTIVE'
    ORDER BY start_date DESC
    LIMIT 1
), academic_teacher AS (
    SELECT teacher.id, teacher.teacher_name
    FROM edu_user_profile_binding binding
    JOIN edu_teacher_profile teacher ON teacher.id = binding.profile_id
    JOIN t_admin_user app_user ON app_user.username = binding.username
    WHERE binding.username = 'academic.demo'
      AND binding.profile_type = 'TEACHER'
      AND binding.status = 'ACTIVE'
      AND teacher.enabled = true
      AND app_user.status = 1
    ORDER BY binding.create_time
    LIMIT 1
)
INSERT INTO edu_scheduling_agent_proposal (
    id,
    semester_code,
    request_text,
    teacher_id,
    teacher_name,
    day_of_week,
    period_no,
    constraint_type,
    reason,
    status,
    create_by,
    create_time,
    last_update_by,
    last_update_time
)
SELECT
    gen_random_uuid()::text,
    current_term.term_code,
    '请优先为' || academic_teacher.teacher_name || '安排周二第2节课程（教务端MVP演示）',
    academic_teacher.id,
    academic_teacher.teacher_name,
    2,
    2,
    'PREFERRED',
    '用于验证教务审批人数据范围、建议确认和驳回入口',
    'DRAFT',
    'academic.demo',
    CURRENT_TIMESTAMP,
    'academic.demo',
    CURRENT_TIMESTAMP
FROM current_term
CROSS JOIN academic_teacher
WHERE NOT EXISTS (
    SELECT 1
    FROM edu_scheduling_agent_proposal existing
    WHERE existing.request_text =
        '请优先为' || academic_teacher.teacher_name || '安排周二第2节课程（教务端MVP演示）'
);

COMMIT;

-- 执行结果摘要。
SELECT 'publication' AS data_type, count(*) AS record_count
FROM msg_publication
WHERE title LIKE '%（MVP演示）'
UNION ALL
SELECT 'publication_recipient', count(*)
FROM msg_publication_recipient recipient
JOIN msg_publication publication ON publication.id = recipient.publication_id
WHERE publication.title LIKE '%（MVP演示）'
UNION ALL
SELECT 'scheduling_agent_proposal', count(*)
FROM edu_scheduling_agent_proposal
WHERE request_text LIKE '%MVP演示）';
