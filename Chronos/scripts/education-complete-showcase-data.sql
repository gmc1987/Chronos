-- Chronos 教育行业完整展示数据
-- 目标数据库：ChronosEducation（PostgreSQL）
-- 特性：可重复执行；使用标准 UUID；复用现有组织、教师、学生和课程主数据。

BEGIN;

SET LOCAL TIME ZONE 'Asia/Shanghai';

-- 为展示账号建立独立教职工和教师档案，避免与现有账号的一对一绑定冲突。
INSERT INTO t_iam_employee (
    id,
    create_by,
    create_time,
    email,
    employee_code,
    employee_name,
    employee_type,
    employment_status,
    gender,
    hire_date,
    phone
)
VALUES
    (
        '24200000-0000-4000-8000-000000000001',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'showcase.teacher@chronos.local',
        'TDEMO001',
        '智慧校园展示教师',
        'FULL_TIME',
        'ACTIVE',
        'MALE',
        DATE '2024-08-20',
        '13900009901'
    ),
    (
        '24200000-0000-4000-8000-000000000002',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'showcase.reviewer@chronos.local',
        'TDEMO002',
        '智慧校园展示教务审核人',
        'FULL_TIME',
        'ACTIVE',
        'FEMALE',
        DATE '2022-08-20',
        '13900009902'
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_teacher_profile (
    id,
    create_by,
    create_time,
    department_id,
    employee_id,
    enabled,
    max_weekly_lessons,
    specialty,
    teacher_name,
    teacher_no,
    max_daily_lessons,
    max_consecutive_lessons,
    employment_status
)
VALUES
    (
        '24300000-0000-4000-8000-000000000001',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        (SELECT id FROM t_organization_unit WHERE organization_unit_code = 'NCVC-MANUFACTURING'),
        '24200000-0000-4000-8000-000000000001',
        true,
        18,
        'PLC控制技术与班级管理',
        '智慧校园展示教师',
        'TDEMO001',
        6,
        4,
        'ACTIVE'
    ),
    (
        '24300000-0000-4000-8000-000000000002',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        (SELECT id FROM t_organization_unit WHERE organization_unit_code = 'NCVC-ACADEMIC-AFFAIRS'),
        '24200000-0000-4000-8000-000000000002',
        true,
        10,
        '教学质量与成绩审核',
        '智慧校园展示教务审核人',
        'TDEMO002',
        4,
        3,
        'ACTIVE'
    )
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 0. 统一展示账号
-- 统一密码：ChronosExam@2026
-- 仅供本地展示和业务验收，生产环境禁止执行本脚本。
-- ---------------------------------------------------------------------------

INSERT INTO t_admin_user (
    id,
    create_by,
    create_time,
    account_locked,
    account_type,
    display_name,
    employee_id,
    failed_login_attempts,
    must_change_password,
    password,
    status,
    token_version,
    username
)
VALUES
    (
        '24000000-0000-4000-8000-000000000001',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        false,
        'ADMIN',
        '智慧校园展示管理员',
        NULL,
        0,
        false,
        '$2y$10$x/5YKiXBT12xr7Hn7qwwkevrIegDAQzqA7wHfHwajyhD3yhmiHw1.',
        1,
        0,
        'showcase.admin'
    ),
    (
        '24000000-0000-4000-8000-000000000002',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        false,
        'STAFF',
        '智慧校园展示教师',
        '24200000-0000-4000-8000-000000000001',
        0,
        false,
        '$2y$10$x/5YKiXBT12xr7Hn7qwwkevrIegDAQzqA7wHfHwajyhD3yhmiHw1.',
        1,
        0,
        'showcase.teacher'
    ),
    (
        '24000000-0000-4000-8000-000000000003',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        false,
        'STUDENT',
        '智慧校园展示学生',
        NULL,
        0,
        false,
        '$2y$10$x/5YKiXBT12xr7Hn7qwwkevrIegDAQzqA7wHfHwajyhD3yhmiHw1.',
        1,
        0,
        'showcase.student'
    ),
    (
        '24000000-0000-4000-8000-000000000004',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        false,
        'PARENT',
        '智慧校园展示家长',
        NULL,
        0,
        false,
        '$2y$10$x/5YKiXBT12xr7Hn7qwwkevrIegDAQzqA7wHfHwajyhD3yhmiHw1.',
        1,
        0,
        'showcase.parent'
    ),
    (
        '24000000-0000-4000-8000-000000000005',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        false,
        'STAFF',
        '智慧校园展示教务审核人',
        '24200000-0000-4000-8000-000000000002',
        0,
        false,
        '$2y$10$x/5YKiXBT12xr7Hn7qwwkevrIegDAQzqA7wHfHwajyhD3yhmiHw1.',
        1,
        0,
        'showcase.reviewer'
    )
ON CONFLICT (username) DO UPDATE
SET display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    password = EXCLUDED.password,
    status = EXCLUDED.status,
    account_locked = false,
    must_change_password = false;

INSERT INTO t_user_role (user_id, role_id)
SELECT account.id, role.id
FROM t_admin_user account
JOIN t_role role
    ON (
        account.username = 'showcase.admin'
        AND role.role_code = 'SUPER_ADMIN'
    )
    OR (
        account.username = 'showcase.teacher'
        AND role.role_code IN (
            'ROLE_PLATFORM_USER',
            'EDU_TEACHER',
            'EDU_CLASS_ADVISOR',
            'WORKFLOW_USER'
        )
    )
    OR (
        account.username = 'showcase.student'
        AND role.role_code IN ('ROLE_PLATFORM_USER', 'WORKFLOW_USER')
    )
    OR (
        account.username = 'showcase.parent'
        AND role.role_code = 'ROLE_PLATFORM_USER'
    )
    OR (
        account.username = 'showcase.reviewer'
        AND role.role_code IN (
            'ROLE_PLATFORM_USER',
            'EDU_ACADEMIC_APPROVER',
            'EDU_GRADE_REVIEWER',
            'EDU_GRADE_PUBLISHER',
            'WORKFLOW_ADMIN'
        )
    )
WHERE account.username LIKE 'showcase.%'
ON CONFLICT DO NOTHING;

-- 展示账号与业务档案采用逐条绑定。学生与家长必须指向同一监护关系，
-- 不能在同一 VALUES 语句中分别选择“第一个空闲档案”，否则两次子查询
-- 会在语句快照内选中不同记录，造成家长绑定为空或违反一对一唯一约束。
INSERT INTO edu_user_profile_binding (
    id,
    create_by,
    create_time,
    username,
    profile_type,
    profile_id,
    status
)
SELECT
    '24100000-0000-4000-8000-000000000001',
    'showcase_seed',
    CURRENT_TIMESTAMP,
    'showcase.teacher',
    'TEACHER',
    teacher.id,
    'ACTIVE'
FROM edu_teacher_profile teacher
WHERE teacher.teacher_no = 'TDEMO001'
ON CONFLICT (username, profile_type) DO UPDATE
SET profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status;

INSERT INTO edu_user_profile_binding (
    id,
    create_by,
    create_time,
    username,
    profile_type,
    profile_id,
    status
)
SELECT
    '24100000-0000-4000-8000-000000000002',
    'showcase_seed',
    CURRENT_TIMESTAMP,
    'showcase.student',
    'STUDENT',
    guardian.student_id,
    'ACTIVE'
FROM edu_student_guardian guardian
JOIN edu_student_profile student
    ON student.id = guardian.student_id
WHERE NOT EXISTS (
    SELECT 1
    FROM edu_user_profile_binding binding
    WHERE binding.profile_type = 'STUDENT'
      AND binding.profile_id = guardian.student_id
      AND binding.username <> 'showcase.student'
)
  AND NOT EXISTS (
    SELECT 1
    FROM edu_user_profile_binding binding
    WHERE binding.profile_type = 'PARENT'
      AND binding.profile_id = guardian.parent_id
      AND binding.username <> 'showcase.parent'
)
ORDER BY student.student_no
LIMIT 1
ON CONFLICT (username, profile_type) DO UPDATE
SET profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status;

INSERT INTO edu_user_profile_binding (
    id,
    create_by,
    create_time,
    username,
    profile_type,
    profile_id,
    status
)
SELECT
    '24100000-0000-4000-8000-000000000003',
    'showcase_seed',
    CURRENT_TIMESTAMP,
    'showcase.parent',
    'PARENT',
    guardian.parent_id,
    'ACTIVE'
FROM edu_user_profile_binding student_binding
JOIN edu_student_guardian guardian
    ON guardian.student_id = student_binding.profile_id
WHERE student_binding.username = 'showcase.student'
  AND student_binding.profile_type = 'STUDENT'
  AND NOT EXISTS (
      SELECT 1
      FROM edu_user_profile_binding existing_binding
      WHERE existing_binding.profile_type = 'PARENT'
        AND existing_binding.profile_id = guardian.parent_id
        AND existing_binding.username <> 'showcase.parent'
  )
ORDER BY guardian.parent_id
LIMIT 1
ON CONFLICT (username, profile_type) DO UPDATE
SET profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status;

INSERT INTO edu_user_profile_binding (
    id,
    create_by,
    create_time,
    username,
    profile_type,
    profile_id,
    status
)
SELECT
    '24100000-0000-4000-8000-000000000004',
    'showcase_seed',
    CURRENT_TIMESTAMP,
    'showcase.reviewer',
    'TEACHER',
    teacher.id,
    'ACTIVE'
FROM edu_teacher_profile teacher
WHERE teacher.teacher_no = 'TDEMO002'
ON CONFLICT (username, profile_type) DO UPDATE
SET profile_id = EXCLUDED.profile_id,
    status = EXCLUDED.status;

-- ---------------------------------------------------------------------------
-- 1. 教室资源与校历
-- ---------------------------------------------------------------------------

INSERT INTO edu_classroom (
    id,
    room_code,
    room_name,
    campus_id,
    building_name,
    capacity,
    room_type,
    enabled,
    create_by,
    create_time,
    equipment_codes
)
VALUES
    (
        '10000000-0000-4000-8000-000000000001',
        'MAIN-A301',
        '主校区教学楼 A301',
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        '教学楼 A',
        48,
        'STANDARD',
        true,
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'PROJECTOR,AUDIO'
    ),
    (
        '10000000-0000-4000-8000-000000000002',
        'MAIN-IT401',
        '主校区计算机实训室 401',
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        '信息楼',
        60,
        'COMPUTER',
        true,
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'COMPUTER,PROJECTOR,INTERNET'
    ),
    (
        '10000000-0000-4000-8000-000000000003',
        'TRAIN-PLC201',
        '实训校区 PLC 实训室 201',
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
        '智能制造实训楼',
        45,
        'LAB',
        true,
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'PLC,PROJECTOR,SAFETY_KIT'
    ),
    (
        '10000000-0000-4000-8000-000000000004',
        'TRAIN-AUTO101',
        '实训校区汽车维修工位 101',
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
        '汽车实训中心',
        40,
        'SPECIAL',
        true,
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'LIFT,DIAGNOSTIC,SAFETY_KIT'
    ),
    (
        '10000000-0000-4000-8000-000000000005',
        'MAIN-MEET201',
        '主校区研讨教室 201',
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        '综合楼',
        30,
        'SEMINAR',
        true,
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'DISPLAY,VIDEO_CONFERENCE'
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_classroom_unavailable_slot (
    id,
    create_by,
    create_time,
    semester_code,
    classroom_id,
    day_of_week,
    start_period,
    end_period,
    reason,
    status
)
VALUES
    (
        '10100000-0000-4000-8000-000000000001',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        '2026-2027-1',
        '10000000-0000-4000-8000-000000000003',
        3,
        7,
        8,
        '设备周检与安全维护',
        'ACTIVE'
    ),
    (
        '10100000-0000-4000-8000-000000000002',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        '2026-2027-1',
        '10000000-0000-4000-8000-000000000002',
        5,
        1,
        2,
        '机房系统镜像更新',
        'ACTIVE'
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_academic_calendar_day (
    id,
    create_by,
    create_time,
    academic_term_id,
    calendar_date,
    day_name,
    day_type,
    remark,
    teaching_day
)
VALUES
    (
        '10200000-0000-4000-8000-000000000001',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        (SELECT id FROM edu_academic_term WHERE term_code = '2026-2027-1'),
        DATE '2026-10-01',
        '国庆节',
        'HOLIDAY',
        '国庆假期，不安排教学',
        false
    ),
    (
        '10200000-0000-4000-8000-000000000002',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        (SELECT id FROM edu_academic_term WHERE term_code = '2026-2027-1'),
        DATE '2026-10-10',
        '调休教学日',
        'MAKEUP_WORKDAY',
        '按周四课表执行',
        true
    ),
    (
        '10200000-0000-4000-8000-000000000003',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        (SELECT id FROM edu_academic_term WHERE term_code = '2026-2027-1'),
        DATE '2026-11-20',
        '技能文化节',
        'ACTIVITY',
        '全天开展校级技能展示活动',
        false
    )
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. 开课任务、合班课、成员和排课过程
-- ---------------------------------------------------------------------------

INSERT INTO edu_course_offering (
    id,
    semester_code,
    offering_code,
    course_code,
    course_name,
    teaching_class_name,
    teacher_id,
    teacher_name,
    student_count,
    weekly_lessons,
    campus_id,
    status,
    create_by,
    create_time,
    required_room_type,
    preferred_duration_periods,
    week_pattern,
    required_equipment_codes,
    offering_mode
)
VALUES
    (
        '11000000-0000-4000-8000-000000000001',
        '2026-2027-1',
        'OFF-2026-JD-PLC',
        'JD201',
        'PLC控制技术',
        '机电技术应用1班 PLC 教学班',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101'),
        '张建国',
        10,
        4,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
        'ACTIVE',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'LAB',
        2,
        'ALL',
        'PLC,PROJECTOR',
        'NORMAL'
    ),
    (
        '11000000-0000-4000-8000-000000000002',
        '2026-2027-1',
        'OFF-2026-SM-VIDEO',
        'SM101',
        '短视频拍摄与制作',
        '数字媒体技术应用1班短视频教学班',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026108'),
        '孙丽',
        10,
        4,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        'ACTIVE',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'COMPUTER',
        2,
        'ALL',
        'COMPUTER,PROJECTOR',
        'NORMAL'
    ),
    (
        '11000000-0000-4000-8000-000000000003',
        '2026-2027-1',
        'OFF-2026-DS-ECOM',
        'DS101',
        '网店运营实务',
        '电子商务1班网店运营教学班',
        (SELECT id FROM edu_teacher_profile ORDER BY teacher_no LIMIT 1),
        (SELECT teacher_name FROM edu_teacher_profile ORDER BY teacher_no LIMIT 1),
        10,
        4,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        'ACTIVE',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'COMPUTER',
        2,
        'ALL',
        'COMPUTER,INTERNET',
        'NORMAL'
    ),
    (
        '11000000-0000-4000-8000-000000000004',
        '2026-2027-1',
        'OFF-2026-QX-AUTO',
        'QX101',
        '汽车发动机构造与维修',
        '汽车运用与维修1班发动机教学班',
        (SELECT id FROM edu_teacher_profile WHERE specialty LIKE '%汽车%' ORDER BY teacher_no LIMIT 1),
        (SELECT teacher_name FROM edu_teacher_profile WHERE specialty LIKE '%汽车%' ORDER BY teacher_no LIMIT 1),
        10,
        4,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
        'ACTIVE',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'SPECIAL',
        2,
        'ALL',
        'LIFT,DIAGNOSTIC',
        'NORMAL'
    ),
    (
        '11000000-0000-4000-8000-000000000005',
        '2026-2027-1',
        'OFF-2026-GG-CHINESE-COMB',
        'GG101',
        '语文',
        '机电与数控公共语文合班',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026109'),
        '黄文杰',
        20,
        2,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
        'ACTIVE',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'STANDARD',
        1,
        'ALL',
        'PROJECTOR',
        'COMBINED'
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_combined_offering_source_class (
    id,
    offering_id,
    administrative_class_id,
    create_by,
    create_time
)
VALUES
    (
        '11100000-0000-4000-8000-000000000001',
        '11000000-0000-4000-8000-000000000005',
        (SELECT id FROM edu_administrative_class WHERE class_code = '2026-JD-01'),
        'showcase_seed',
        CURRENT_TIMESTAMP
    ),
    (
        '11100000-0000-4000-8000-000000000002',
        '11000000-0000-4000-8000-000000000005',
        (SELECT id FROM edu_administrative_class WHERE class_code = '2026-SK-01'),
        'showcase_seed',
        CURRENT_TIMESTAMP
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_teaching_class_member (
    id,
    create_by,
    create_time,
    enrolled_at,
    enrollment_status,
    offering_id,
    student_id,
    enrollment_source
)
SELECT
    md5('showcase-member-' || offering.id || '-' || student.id)::uuid::text,
    'showcase_seed',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    'ENROLLED',
    offering.id,
    student.id,
    CASE
        WHEN offering.offering_mode = 'COMBINED' THEN 'SOURCE_CLASS'
        ELSE 'MANUAL'
    END
FROM edu_course_offering offering
JOIN edu_student_profile student
    ON (
        offering.offering_code = 'OFF-2026-JD-PLC'
        AND student.administrative_class_id = (
            SELECT id FROM edu_administrative_class WHERE class_code = '2026-JD-01'
        )
    )
    OR (
        offering.offering_code = 'OFF-2026-SM-VIDEO'
        AND student.administrative_class_id = (
            SELECT id FROM edu_administrative_class WHERE class_code = '2026-SM-01'
        )
    )
    OR (
        offering.offering_code = 'OFF-2026-DS-ECOM'
        AND student.administrative_class_id = (
            SELECT id FROM edu_administrative_class WHERE class_code = '2026-DS-01'
        )
    )
    OR (
        offering.offering_code = 'OFF-2026-QX-AUTO'
        AND student.administrative_class_id = (
            SELECT id FROM edu_administrative_class WHERE class_code = '2026-QX-01'
        )
    )
    OR (
        offering.offering_code = 'OFF-2026-GG-CHINESE-COMB'
        AND student.administrative_class_id IN (
            SELECT id
            FROM edu_administrative_class
            WHERE class_code IN ('2026-JD-01', '2026-SK-01')
        )
    )
WHERE offering.id LIKE '11000000-%'
ON CONFLICT DO NOTHING;

INSERT INTO edu_schedule_policy (
    id,
    create_by,
    create_time,
    semester_code,
    default_max_weekly_lessons,
    default_max_daily_lessons,
    default_max_consecutive_lessons,
    scheduled_lesson_reward,
    preferred_slot_reward,
    same_course_day_penalty,
    teacher_load_penalty,
    consecutive_penalty,
    campus_switch_penalty,
    unscheduled_lesson_penalty,
    course_concentration_threshold,
    block_teacher_overload,
    block_hard_conflicts,
    block_incomplete_offerings,
    teacher_gap_penalty,
    minimum_campus_travel_periods
)
VALUES (
    '12000000-0000-4000-8000-000000000001',
    'showcase_seed',
    CURRENT_TIMESTAMP,
    '2026-2027-1',
    20,
    6,
    4,
    100,
    15,
    8,
    3,
    6,
    30,
    1000,
    2,
    true,
    true,
    true,
    4,
    2
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_teacher_time_constraint (
    id,
    create_by,
    create_time,
    constraint_type,
    day_of_week,
    period_no,
    reason,
    semester_code,
    teacher_id,
    weight
)
VALUES
    (
        '12000000-0000-4000-8000-000000000011',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'UNAVAILABLE',
        2,
        1,
        '每周二第一节参加系部教学例会',
        '2026-2027-1',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101'),
        100
    ),
    (
        '12000000-0000-4000-8000-000000000012',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        'PREFERRED',
        4,
        3,
        '优先安排连续两节实训课',
        '2026-2027-1',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026108'),
        20
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_schedule_generation_job (
    id,
    create_by,
    create_time,
    semester_code,
    request_json,
    status,
    progress,
    result_candidate_ids,
    requested_by,
    started_at,
    finished_at
)
VALUES
    (
        '12100000-0000-4000-8000-000000000001',
        'admin',
        CURRENT_TIMESTAMP - INTERVAL '2 day',
        '2026-2027-1',
        '{"mode":"FULL","scope":"ALL","optimize":"BALANCED"}',
        'SUCCEEDED',
        100,
        '["12200000-0000-4000-8000-000000000001"]',
        'admin',
        CURRENT_TIMESTAMP - INTERVAL '2 day',
        CURRENT_TIMESTAMP - INTERVAL '2 day' + INTERVAL '2 minute'
    ),
    (
        '12100000-0000-4000-8000-000000000002',
        'admin',
        CURRENT_TIMESTAMP - INTERVAL '1 hour',
        '2026-2027-1',
        '{"mode":"LOCAL","scope":{"offeringCode":"OFF-2026-JD-PLC"}}',
        'RUNNING',
        65,
        NULL,
        'admin',
        CURRENT_TIMESTAMP - INTERVAL '55 minute',
        NULL
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_schedule_candidate_plan (
    id,
    create_by,
    create_time,
    semester_code,
    plan_name,
    generation_mode,
    scope_json,
    baseline_hash,
    snapshot_json,
    metrics_json,
    entry_count,
    unscheduled_lessons,
    total_score,
    status,
    generated_by,
    generated_at,
    review_status,
    owner_username,
    collaboration_remark,
    reviewed_by,
    reviewed_at,
    review_comment
)
VALUES (
    '12200000-0000-4000-8000-000000000001',
    'admin',
    CURRENT_TIMESTAMP - INTERVAL '2 day',
    '2026-2027-1',
    '2026 秋季全校课表候选方案 A',
    'FULL',
    '{"campuses":["MAIN","TRAIN"],"weeks":20}',
    'showcase-baseline-2026-fall',
    '{"description":"全校课程、教师和教室资源排课快照"}',
    '{"hardConflicts":0,"softConflicts":3,"roomUtilization":0.78,"teacherBalance":0.86}',
    21,
    0,
    9860,
    'CANDIDATE',
    'admin',
    CURRENT_TIMESTAMP - INTERVAL '2 day',
    'APPROVED',
    'admin',
    '教务处与两个校区已完成联合复核',
    'edu.reviewer.qa',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    '无硬冲突，允许发布'
)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. 教学中心、备课、教研和资源
-- ---------------------------------------------------------------------------

INSERT INTO edu_preparation_member (
    id,
    preparation_id,
    teacher_id,
    role,
    joined_at,
    invitation_status,
    invited_by,
    invited_at,
    responded_at,
    response_comment
)
SELECT
    '13000000-0000-4000-8000-000000000001',
    preparation.id,
    teacher.id,
    'MEMBER',
    CURRENT_TIMESTAMP - INTERVAL '7 day',
    'ACCEPTED',
    preparation.create_by,
    CURRENT_TIMESTAMP - INTERVAL '8 day',
    CURRENT_TIMESTAMP - INTERVAL '7 day',
    '已接受邀请，负责实训安全环节'
FROM edu_preparation preparation
CROSS JOIN LATERAL (
    SELECT id
    FROM edu_teacher_profile
    WHERE id <> preparation.owner_teacher_id
    ORDER BY teacher_no
    LIMIT 1
) teacher
ORDER BY preparation.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_preparation_comment (
    id,
    preparation_id,
    author_id,
    content,
    create_time,
    audit_action,
    archived
)
SELECT
    '13100000-0000-4000-8000-000000000001',
    preparation.id,
    teacher.id,
    '建议在演示环节增加设备断电与急停操作说明。',
    CURRENT_TIMESTAMP - INTERVAL '6 day',
    'CREATE',
    false
FROM edu_preparation preparation
CROSS JOIN LATERAL (
    SELECT id FROM edu_teacher_profile ORDER BY teacher_no LIMIT 1
) teacher
ORDER BY preparation.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_preparation_material (
    id,
    preparation_id,
    file_id,
    title,
    metadata_json,
    bind_state,
    bound_at,
    create_by,
    create_time
)
SELECT
    '13200000-0000-4000-8000-000000000001',
    preparation.id,
    managed_file.id,
    '集体备课安全检查清单',
    '{"category":"CHECKLIST","description":"课前设备与安全检查"}',
    'BOUND',
    CURRENT_TIMESTAMP - INTERVAL '5 day',
    preparation.create_by,
    CURRENT_TIMESTAMP - INTERVAL '5 day'
FROM edu_preparation preparation
CROSS JOIN LATERAL (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
) managed_file
ORDER BY preparation.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_lesson_plan_review (
    id,
    lesson_plan_version_id,
    reviewer_id,
    decision,
    comment,
    reviewed_at
)
SELECT
    '13300000-0000-4000-8000-000000000001',
    version.id,
    reviewer.id,
    'APPROVED',
    '教学目标、实训步骤和安全提示完整，同意发布。',
    CURRENT_TIMESTAMP - INTERVAL '4 day'
FROM edu_lesson_plan_version version
CROSS JOIN LATERAL (
    SELECT id FROM edu_teacher_profile ORDER BY teacher_no DESC LIMIT 1
) reviewer
ORDER BY version.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_courseware_version (
    id,
    courseware_id,
    version_no,
    file_id,
    metadata_json,
    status,
    create_by,
    create_time,
    bind_state,
    bound_at,
    published_at,
    file_name,
    mime_type,
    file_size,
    checksum_sha256,
    uploaded_at
)
SELECT
    '13400000-0000-4000-8000-000000000001',
    courseware.id,
    1,
    managed_file.id,
    '{"slides":32,"language":"zh-CN","scenario":"课堂演示"}',
    'PUBLISHED',
    courseware.create_by,
    CURRENT_TIMESTAMP - INTERVAL '10 day',
    'BOUND',
    CURRENT_TIMESTAMP - INTERVAL '10 day',
    CURRENT_TIMESTAMP - INTERVAL '9 day',
    'PLC控制技术第一章课件.pptx',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    1048576,
    repeat('a', 64),
    CURRENT_TIMESTAMP - INTERVAL '10 day'
FROM edu_courseware courseware
CROSS JOIN LATERAL (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
) managed_file
ORDER BY courseware.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_teaching_center_resource (
    id,
    create_by,
    create_time,
    archived,
    category,
    content,
    file_id,
    metadata_json,
    offering_id,
    resource_type,
    status,
    title,
    version_no
)
VALUES
    (
        '13500000-0000-4000-8000-000000000001',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '6 day',
        false,
        'CASE',
        'PLC 顺序控制课堂案例，包含任务单、梯形图和评分标准。',
        (SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1),
        '{"tags":["PLC","项目教学"],"visibility":"SCHOOL"}',
        '11000000-0000-4000-8000-000000000001',
        'TEACHING_RESOURCE',
        'PUBLISHED',
        'PLC 顺序控制项目案例包',
        1
    ),
    (
        '13500000-0000-4000-8000-000000000002',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        false,
        'MICRO_LESSON',
        '短视频运镜基础微课脚本与示范素材。',
        NULL,
        '{"durationMinutes":12,"visibility":"TEACHING_CLASS"}',
        '11000000-0000-4000-8000-000000000002',
        'MICRO_LESSON',
        'DRAFT',
        '短视频运镜基础微课',
        1
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_research_group (
    id,
    name,
    subject_id,
    status,
    archived,
    create_by,
    create_time,
    school_id,
    campus_id,
    leader_teacher_id,
    course_scope_json,
    description
)
VALUES (
    '13600000-0000-4000-8000-000000000001',
    '智能制造课程教研组',
    (SELECT id FROM edu_subject WHERE subject_code = 'SUB-PLC'),
    'ACTIVE',
    false,
    'teacher.demo',
    CURRENT_TIMESTAMP - INTERVAL '30 day',
    (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC'),
    (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
    (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101'),
    '["JD201"]'::jsonb,
    '负责 PLC、电气控制相关课程标准、资源和课堂改进。'
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_research_group_member (
    id,
    group_id,
    teacher_id,
    role
)
SELECT
    md5('showcase-research-member-' || teacher.id)::uuid::text,
    '13600000-0000-4000-8000-000000000001',
    teacher.id,
    CASE WHEN teacher.teacher_no = 'T2026101' THEN 'LEADER' ELSE 'MEMBER' END
FROM edu_teacher_profile teacher
WHERE teacher.teacher_no IN ('T2026101', 'T2026103', 'T2026107')
ON CONFLICT DO NOTHING;

INSERT INTO edu_research_activity (
    id,
    group_id,
    title,
    status,
    activity_time,
    content,
    create_by,
    create_time,
    archived,
    end_time,
    location,
    agenda,
    organizer_id,
    minutes,
    course_id
)
VALUES
    (
        '13700000-0000-4000-8000-000000000001',
        '13600000-0000-4000-8000-000000000001',
        'PLC 项目化教学公开课研讨',
        'COMPLETED',
        CURRENT_TIMESTAMP - INTERVAL '12 day',
        '围绕项目任务拆解、过程评价和安全规范开展集体研讨。',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '20 day',
        false,
        CURRENT_TIMESTAMP - INTERVAL '12 day' + INTERVAL '2 hour',
        '实训校区 PLC 实训室 201',
        '公开课观摩；学生作品点评；评价量规修订',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101'),
        '形成新版项目任务单，并统一实训安全检查要求。',
        (SELECT id FROM edu_course_catalog WHERE course_code = 'JD201')
    ),
    (
        '13700000-0000-4000-8000-000000000002',
        '13600000-0000-4000-8000-000000000001',
        '期中教学质量专题教研',
        'PLANNED',
        CURRENT_TIMESTAMP + INTERVAL '10 day',
        '分析期中成绩与课堂督导反馈，形成改进计划。',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '2 day',
        false,
        CURRENT_TIMESTAMP + INTERVAL '10 day 2 hour',
        '主校区研讨教室 201',
        '成绩分析；错题聚类；后续教学安排',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101'),
        NULL,
        (SELECT id FROM edu_course_catalog WHERE course_code = 'JD201')
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_research_activity_member (
    id,
    activity_id,
    teacher_id,
    role,
    attendance_status,
    attendance_at,
    responded_at,
    invitation_status,
    attendance_updated_by
)
SELECT
    md5('showcase-activity-member-' || activity.id || '-' || member.teacher_id)::uuid::text,
    activity.id,
    member.teacher_id,
    member.role,
    CASE WHEN activity.status = 'COMPLETED' THEN 'PRESENT' ELSE 'INVITED' END,
    CASE WHEN activity.status = 'COMPLETED' THEN activity.activity_time ELSE NULL END,
    CURRENT_TIMESTAMP - INTERVAL '15 day',
    'ACCEPTED',
    (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101')
FROM edu_research_activity activity
JOIN edu_research_group_member member
    ON member.group_id = activity.group_id
WHERE activity.id LIKE '13700000-%'
ON CONFLICT DO NOTHING;

INSERT INTO edu_research_material (
    id,
    activity_id,
    file_id,
    title,
    create_by,
    create_time
)
VALUES (
    '13800000-0000-4000-8000-000000000001',
    '13700000-0000-4000-8000-000000000001',
    (SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1),
    'PLC 项目化教学评价量规',
    'teacher.demo',
    CURRENT_TIMESTAMP - INTERVAL '12 day'
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_research_result (
    id,
    activity_id,
    title,
    content,
    file_id,
    status,
    result_type,
    published_at
)
VALUES (
    '13900000-0000-4000-8000-000000000001',
    '13700000-0000-4000-8000-000000000001',
    'PLC 项目化教学改进方案',
    '统一项目任务书、过程评分量规和安全检查单，自第八教学周起执行。',
    (SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1),
    'PUBLISHED',
    'IMPROVEMENT_PLAN',
    CURRENT_TIMESTAMP - INTERVAL '11 day'
)
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 4. 考试、成绩更正、补考和重修
-- ---------------------------------------------------------------------------

INSERT INTO edu_exam_session_offering (
    id,
    create_by,
    create_time,
    session_id,
    offering_id
)
VALUES
    (
        '14000000-0000-4000-8000-000000000001',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        (SELECT id FROM edu_exam_session ORDER BY create_time LIMIT 1),
        '11000000-0000-4000-8000-000000000001'
    ),
    (
        '14000000-0000-4000-8000-000000000002',
        'showcase_seed',
        CURRENT_TIMESTAMP,
        (SELECT id FROM edu_exam_session ORDER BY create_time DESC LIMIT 1),
        '11000000-0000-4000-8000-000000000005'
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_exam_teacher_qualification (
    id,
    teacher_id,
    subject_id,
    create_by,
    create_time
)
VALUES
    (
        '14100000-0000-4000-8000-000000000001',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101'),
        (SELECT id FROM edu_subject WHERE subject_code = 'SUB-PLC'),
        'showcase_seed',
        CURRENT_TIMESTAMP
    ),
    (
        '14100000-0000-4000-8000-000000000002',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026109'),
        (SELECT id FROM edu_subject WHERE subject_name = '语文'),
        'showcase_seed',
        CURRENT_TIMESTAMP
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_grade_correction (
    id,
    create_by,
    create_time,
    gradebook_id,
    base_version,
    target_version,
    correction_json,
    status,
    requested_by,
    published_by,
    reason
)
SELECT
    '15000000-0000-4000-8000-000000000001',
    'teacher.demo',
    CURRENT_TIMESTAMP - INTERVAL '5 day',
    gradebook.id,
    1,
    2,
    json_build_object(
        'studentId', course_grade.student_id,
        'beforeScore', course_grade.total_score,
        'afterScore', course_grade.total_score + 2,
        'reason', '复核发现实训过程分漏登'
    )::text,
    'PUBLISHED',
    'teacher.demo',
    'edu.reviewer.qa',
    '学生提交原始评分记录后复核通过'
FROM edu_gradebook gradebook
JOIN edu_course_grade course_grade
    ON course_grade.gradebook_id = gradebook.id
ORDER BY course_grade.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_grade_change_request (
    id,
    create_by,
    create_time,
    gradebook_id,
    course_grade_id,
    student_id,
    before_score,
    after_score,
    reason,
    workflow_instance_id,
    status,
    approved_by,
    approved_at
)
SELECT
    '15100000-0000-4000-8000-000000000001',
    'teacher.demo',
    CURRENT_TIMESTAMP - INTERVAL '3 day',
    course_grade.gradebook_id,
    course_grade.id,
    course_grade.student_id,
    course_grade.total_score,
    LEAST(100, course_grade.total_score + 3),
    '试卷复核后确认一道题计分遗漏',
    'showcase-grade-change-workflow-001',
    'APPROVED',
    'edu.reviewer.qa',
    CURRENT_TIMESTAMP - INTERVAL '2 day'
FROM edu_course_grade course_grade
ORDER BY course_grade.create_time DESC
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_grade_change_incident (
    id,
    create_by,
    create_time,
    change_request_id,
    workflow_instance_id,
    status,
    retry_count,
    last_error,
    resolved_by,
    resolved_at,
    resolution_note
)
VALUES (
    '15200000-0000-4000-8000-000000000001',
    'SYSTEM',
    CURRENT_TIMESTAMP - INTERVAL '2 day',
    '15100000-0000-4000-8000-000000000001',
    'showcase-grade-change-workflow-001',
    'RESOLVED',
    1,
    '首次回写时成绩版本已变化',
    'edu.reviewer.qa',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    '重新读取最新版本后成功完成成绩回写'
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_makeup_exam_record (
    id,
    create_by,
    create_time,
    gradebook_id,
    source_grade_id,
    student_id,
    attempt_type,
    result_score,
    status,
    published_grade_id,
    remark
)
SELECT
    '15300000-0000-4000-8000-000000000001',
    'edu.reviewer.qa',
    CURRENT_TIMESTAMP - INTERVAL '20 day',
    course_grade.gradebook_id,
    course_grade.id,
    course_grade.student_id,
    'MAKEUP',
    68,
    'PUBLISHED',
    course_grade.id,
    '补考成绩按学校规则最高记 60 分，原始补考卷面分 68 分'
FROM edu_course_grade course_grade
WHERE course_grade.passed = false
ORDER BY course_grade.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_makeup_retake_record (
    id,
    create_by,
    create_time,
    source_course_grade_id,
    source_gradebook_id,
    student_id,
    offering_id,
    exam_session_id,
    exam_candidate_id,
    record_type,
    result_score,
    max_score,
    original_score,
    effective_score,
    strategy,
    status,
    source_snapshot_hash,
    published_snapshot_json,
    submitted_by,
    approved_by,
    published_by,
    submitted_at,
    approved_at,
    published_at
)
SELECT
    '15400000-0000-4000-8000-000000000001',
    'teacher.demo',
    CURRENT_TIMESTAMP - INTERVAL '15 day',
    course_grade.id,
    course_grade.gradebook_id,
    course_grade.student_id,
    '11000000-0000-4000-8000-000000000001',
    candidate.session_id,
    candidate.id,
    'RETAKE',
    75,
    100,
    course_grade.total_score,
    75,
    'LATEST_SCORE',
    'PUBLISHED',
    course_grade.snapshot_hash,
    '{"effectiveScore":75,"strategy":"LATEST_SCORE"}',
    'teacher.demo',
    'edu.reviewer.qa',
    'edu.reviewer.qa',
    CURRENT_TIMESTAMP - INTERVAL '14 day',
    CURRENT_TIMESTAMP - INTERVAL '13 day',
    CURRENT_TIMESTAMP - INTERVAL '12 day'
FROM edu_course_grade course_grade
CROSS JOIN LATERAL (
    SELECT candidate.id, room.session_id
    FROM edu_exam_candidate candidate
    JOIN edu_exam_room room
        ON room.id = candidate.room_id
    WHERE candidate.student_id = course_grade.student_id
    ORDER BY candidate.id
    LIMIT 1
) candidate
ORDER BY course_grade.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 5. 会议执行闭环
-- ---------------------------------------------------------------------------

INSERT INTO edu_meeting_minutes (
    id,
    create_by,
    create_time,
    meeting_id,
    content,
    decisions_text,
    status,
    published_at
)
SELECT
    '16000000-0000-4000-8000-000000000001',
    meeting.organizer_username,
    CURRENT_TIMESTAMP - INTERVAL '8 day',
    meeting.id,
    '会议完成平台上线情况复盘，确认排课、考试、成绩和门户展示进入联合验收阶段。',
    '一、教务处负责业务验收；二、信息中心跟踪问题；三、各系部完成基础数据复核。',
    'PUBLISHED',
    CURRENT_TIMESTAMP - INTERVAL '8 day'
FROM edu_meeting meeting
ORDER BY meeting.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_meeting_action_item (
    id,
    create_by,
    create_time,
    meeting_id,
    title,
    description,
    assignee_username,
    due_at,
    status,
    completed_at
)
SELECT
    '16100000-0000-4000-8000-000000000001',
    meeting.organizer_username,
    CURRENT_TIMESTAMP - INTERVAL '8 day',
    meeting.id,
    '完成全业务演示数据复核',
    '逐模块确认页面数据、状态和业务关联是否符合展示要求。',
    'admin',
    CURRENT_TIMESTAMP + INTERVAL '5 day',
    'IN_PROGRESS',
    NULL
FROM edu_meeting meeting
ORDER BY meeting.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_meeting_material (
    id,
    create_by,
    create_time,
    meeting_id,
    title,
    file_id
)
SELECT
    '16200000-0000-4000-8000-000000000001',
    meeting.organizer_username,
    CURRENT_TIMESTAMP - INTERVAL '9 day',
    meeting.id,
    '智慧校园联合验收检查表',
    managed_file.id
FROM edu_meeting meeting
CROSS JOIN LATERAL (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
) managed_file
ORDER BY meeting.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 6. 家校协同、班级通知、学籍和教师异动
-- ---------------------------------------------------------------------------

INSERT INTO edu_parent_account_binding (
    id,
    parent_id,
    username,
    status,
    verified_at,
    create_by,
    create_time
)
SELECT
    md5('showcase-parent-binding-' || parent.id)::uuid::text,
    parent.id,
    'parent.' || lower(parent.parent_no),
    'ACTIVE',
    CURRENT_TIMESTAMP - INTERVAL '20 day',
    'showcase_seed',
    CURRENT_TIMESTAMP - INTERVAL '20 day'
FROM edu_parent_profile parent
ORDER BY parent.parent_no
ON CONFLICT DO NOTHING;

INSERT INTO edu_parent_account_binding (
    id,
    parent_id,
    username,
    status,
    verified_at,
    create_by,
    create_time
)
VALUES (
    '24100000-0000-4000-8000-000000000005',
    (
        SELECT profile_id
        FROM edu_user_profile_binding
        WHERE username = 'showcase.parent'
          AND profile_type = 'PARENT'
    ),
    'showcase.parent',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    'showcase_seed',
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_class_notice (
    id,
    create_by,
    create_time,
    class_id,
    title,
    content,
    require_receipt,
    receipt_deadline,
    status,
    publisher_username,
    published_at
)
VALUES
    (
        '17000000-0000-4000-8000-000000000001',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '3 day',
        (SELECT id FROM edu_administrative_class WHERE class_code = '2026-JD-01'),
        '机电技术应用1班期中家长会通知',
        '请家长于本周五 19:00 参加线上家长会，会议将说明期中学习情况和实训安全要求。',
        true,
        CURRENT_TIMESTAMP + INTERVAL '2 day',
        'PUBLISHED',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '3 day'
    ),
    (
        '17000000-0000-4000-8000-000000000002',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        (SELECT id FROM edu_administrative_class WHERE class_code = '2026-JD-01'),
        '下周实训耗材准备提醒',
        '下周开展 PLC 接线实训，请学生准备工具包并按要求穿着实训服。',
        false,
        NULL,
        'PUBLISHED',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '1 day'
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_class_notice_recipient (
    id,
    create_by,
    create_time,
    notice_id,
    student_id,
    parent_id,
    recipient_username,
    delivered_at,
    read_at,
    acknowledged_at,
    acknowledgement
)
SELECT
    md5('showcase-class-notice-recipient-' || notice.id || '-' || guardian.student_id)::uuid::text,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    notice.id,
    guardian.student_id,
    guardian.parent_id,
    binding.username,
    notice.published_at,
    CASE WHEN row_number() OVER (PARTITION BY notice.id ORDER BY guardian.student_id) <= 6
        THEN notice.published_at + INTERVAL '2 hour'
        ELSE NULL
    END,
    CASE WHEN row_number() OVER (PARTITION BY notice.id ORDER BY guardian.student_id) <= 4
        THEN notice.published_at + INTERVAL '4 hour'
        ELSE NULL
    END,
    CASE WHEN row_number() OVER (PARTITION BY notice.id ORDER BY guardian.student_id) <= 4
        THEN '已知悉，将按时参加。'
        ELSE NULL
    END
FROM edu_class_notice notice
JOIN edu_student_guardian guardian
    ON guardian.student_id IN (
        SELECT id
        FROM edu_student_profile
        WHERE administrative_class_id = notice.class_id
    )
JOIN edu_parent_account_binding binding
    ON binding.parent_id = guardian.parent_id
WHERE notice.id LIKE '17000000-%'
ON CONFLICT DO NOTHING;

INSERT INTO edu_home_notice (
    id,
    school_id,
    class_id,
    title,
    content,
    receipt_required,
    publish_at,
    expire_at,
    status,
    publisher_username,
    create_by,
    create_time
)
VALUES (
    '17100000-0000-4000-8000-000000000001',
    (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC'),
    (SELECT id FROM edu_administrative_class WHERE class_code = '2026-JD-01'),
    '实训安全家校告知书',
    '请家长与学生共同阅读实训室安全规范，并在规定时间内完成回执。',
    true,
    CURRENT_TIMESTAMP - INTERVAL '5 day',
    CURRENT_TIMESTAMP + INTERVAL '10 day',
    'PUBLISHED',
    'teacher.demo',
    'teacher.demo',
    CURRENT_TIMESTAMP - INTERVAL '5 day'
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_home_notice_target (
    id,
    notice_id,
    student_id,
    parent_id,
    delivery_status,
    read_at,
    receipt_status,
    receipt_at,
    receipt_comment,
    create_by,
    create_time
)
SELECT
    md5('showcase-home-notice-target-' || guardian.student_id)::uuid::text,
    '17100000-0000-4000-8000-000000000001',
    guardian.student_id,
    guardian.parent_id,
    'DELIVERED',
    CASE WHEN row_number() OVER (ORDER BY guardian.student_id) <= 6
        THEN CURRENT_TIMESTAMP - INTERVAL '3 day'
        ELSE NULL
    END,
    CASE WHEN row_number() OVER (ORDER BY guardian.student_id) <= 4
        THEN 'RECEIVED'
        ELSE 'PENDING'
    END,
    CASE WHEN row_number() OVER (ORDER BY guardian.student_id) <= 4
        THEN CURRENT_TIMESTAMP - INTERVAL '2 day'
        ELSE NULL
    END,
    CASE WHEN row_number() OVER (ORDER BY guardian.student_id) <= 4
        THEN '已阅读并提醒孩子遵守安全规范。'
        ELSE NULL
    END,
    'SYSTEM',
    CURRENT_TIMESTAMP - INTERVAL '5 day'
FROM edu_student_guardian guardian
WHERE guardian.student_id IN (
    SELECT id
    FROM edu_student_profile
    WHERE administrative_class_id = (
        SELECT id FROM edu_administrative_class WHERE class_code = '2026-JD-01'
    )
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_student_status_change (
    id,
    create_by,
    create_time,
    student_id,
    change_type,
    from_status,
    to_status,
    from_grade_id,
    to_grade_id,
    from_major_id,
    to_major_id,
    from_class_id,
    to_class_id,
    effective_date,
    reason,
    status,
    requested_by,
    requested_at,
    decided_by,
    decided_at,
    decision_comment,
    applied_at
)
SELECT
    '17200000-0000-4000-8000-000000000001',
    'admin',
    CURRENT_TIMESTAMP - INTERVAL '10 day',
    student.id,
    'TRANSFER_CLASS',
    student.enrollment_status,
    student.enrollment_status,
    student.grade_id,
    student.grade_id,
    student.major_id,
    student.major_id,
    student.administrative_class_id,
    (SELECT id FROM edu_administrative_class WHERE class_code = '2026-SK-01'),
    CURRENT_DATE + 7,
    '学生申请转入数控技术方向，已完成家长和系部确认。',
    'APPROVED_PENDING',
    'admin',
    CURRENT_TIMESTAMP - INTERVAL '10 day',
    'edu.reviewer.qa',
    CURRENT_TIMESTAMP - INTERVAL '8 day',
    '同意，自下周一生效。',
    NULL
FROM edu_student_profile student
WHERE student.administrative_class_id = (
    SELECT id FROM edu_administrative_class WHERE class_code = '2026-JD-01'
)
ORDER BY student.student_no
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_teacher_employment_change (
    id,
    create_by,
    create_time,
    teacher_id,
    change_type,
    from_status,
    to_status,
    from_department_id,
    to_department_id,
    effective_date,
    reason,
    status,
    created_by,
    applied_at
)
SELECT
    '17300000-0000-4000-8000-000000000001',
    'admin',
    CURRENT_TIMESTAMP - INTERVAL '15 day',
    teacher.id,
    'TRANSFER',
    teacher.employment_status,
    teacher.employment_status,
    teacher.department_id,
    (SELECT id FROM t_organization_unit WHERE organization_unit_code = 'NCVC-IT'),
    CURRENT_DATE + 30,
    '承担跨系数字化课程建设任务。',
    'SCHEDULED',
    'admin',
    NULL
FROM edu_teacher_profile teacher
WHERE teacher.teacher_no = 'T2026107'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 7. 教室预约、督导和数据中心
-- ---------------------------------------------------------------------------

INSERT INTO edu_classroom_reservation (
    id,
    create_by,
    create_time,
    workflow_instance_id,
    business_key,
    applicant_username,
    applicant_id,
    semester_code,
    classroom_id,
    usage_date,
    start_period,
    duration_periods,
    attendee_count,
    purpose,
    status,
    approved_by
)
VALUES
    (
        '18000000-0000-4000-8000-000000000001',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '3 day',
        'showcase-room-workflow-approved',
        'ROOM-RES-2026-001',
        'teacher.demo',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026101'),
        '2026-2027-1',
        '10000000-0000-4000-8000-000000000003',
        CURRENT_DATE + 5,
        7,
        2,
        40,
        'PLC 技能竞赛赛前集训',
        'APPROVED',
        'edu.reviewer.qa'
    ),
    (
        '18000000-0000-4000-8000-000000000002',
        'teacher.demo',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        'showcase-room-workflow-pending',
        'ROOM-RES-2026-002',
        'teacher.demo',
        (SELECT id FROM edu_teacher_profile WHERE teacher_no = 'T2026108'),
        '2026-2027-1',
        '10000000-0000-4000-8000-000000000002',
        CURRENT_DATE + 8,
        5,
        2,
        48,
        '短视频作品集中剪辑与点评',
        'PENDING',
        NULL
    )
ON CONFLICT DO NOTHING;

INSERT INTO edu_supervision_form_template (
    id,
    create_by,
    create_time,
    school_id,
    name,
    form_definition_id,
    version_no,
    status
)
VALUES (
    '18100000-0000-4000-8000-000000000001',
    'admin',
    CURRENT_TIMESTAMP - INTERVAL '20 day',
    (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC'),
    '课堂教学质量督导评价表',
    (SELECT id FROM form_definition ORDER BY create_time LIMIT 1),
    1,
    'PUBLISHED'
)
ON CONFLICT DO NOTHING;

INSERT INTO data_grade_event_fact (
    id,
    create_by,
    create_time,
    event_id,
    event_type,
    aggregate_id,
    occurred_at,
    offering_id,
    student_id,
    score,
    max_score,
    source_version
)
SELECT
    md5('showcase-grade-fact-' || course_grade.id)::uuid::text,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'showcase-grade-published-' || course_grade.id,
    'GRADE_PUBLISHED',
    course_grade.gradebook_id,
    course_grade.create_time,
    gradebook.offering_id,
    course_grade.student_id,
    course_grade.total_score,
    100,
    course_grade.version_no::text
FROM edu_course_grade course_grade
JOIN edu_gradebook gradebook
    ON gradebook.id = course_grade.gradebook_id
ORDER BY course_grade.create_time
LIMIT 20
ON CONFLICT DO NOTHING;

INSERT INTO data_metric_snapshot (
    id,
    create_by,
    create_time,
    snapshot_date,
    campus_id,
    metric_code,
    metric_value,
    dimension_json,
    source_version
)
VALUES
    (
        '19000000-0000-4000-8000-000000000001',
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_DATE,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        'STUDENT_ATTENDANCE_RATE',
        96.80,
        '{"term":"2026-2027-1","gradeYear":2026}',
        'showcase-v1'
    ),
    (
        '19000000-0000-4000-8000-000000000002',
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_DATE,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
        'CLASSROOM_UTILIZATION_RATE',
        78.50,
        '{"term":"2026-2027-1","roomType":"LAB"}',
        'showcase-v1'
    ),
    (
        '19000000-0000-4000-8000-000000000003',
        'SYSTEM',
        CURRENT_TIMESTAMP,
        CURRENT_DATE,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC'),
        'COURSE_PASS_RATE',
        91.20,
        '{"term":"2026-2027-1"}',
        'showcase-v1'
    )
ON CONFLICT DO NOTHING;

INSERT INTO data_quality_issue (
    id,
    create_by,
    create_time,
    rule_id,
    campus_id,
    metric_code,
    title,
    description,
    severity,
    status,
    owner_id,
    due_date,
    resolution,
    resolved_at,
    detected_date
)
VALUES
    (
        '19100000-0000-4000-8000-000000000001',
        'SYSTEM',
        CURRENT_TIMESTAMP - INTERVAL '2 day',
        (SELECT id FROM data_quality_rule ORDER BY id LIMIT 1),
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        'GRADE_COMPLETENESS',
        '一个教学班成绩明细不完整',
        '成绩册已发布，但仍有两名学生缺少过程性评价明细。',
        'HIGH',
        'OPEN',
        'edu.reviewer.qa',
        CURRENT_DATE + 3,
        NULL,
        NULL,
        CURRENT_DATE - 2
    ),
    (
        '19100000-0000-4000-8000-000000000002',
        'SYSTEM',
        CURRENT_TIMESTAMP - INTERVAL '8 day',
        (SELECT id FROM data_quality_rule ORDER BY id DESC LIMIT 1),
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-TRAIN'),
        'ROOM_CAPACITY_MATCH',
        '合班课教室容量校验已修复',
        '原候选方案教室容量小于合班人数，调整至大教室后重新校验通过。',
        'MEDIUM',
        'RESOLVED',
        'admin',
        CURRENT_DATE - 3,
        '重新分配教室并更新排课候选方案。',
        CURRENT_TIMESTAMP - INTERVAL '5 day',
        CURRENT_DATE - 8
    )
ON CONFLICT DO NOTHING;

INSERT INTO data_report_task (
    id,
    create_by,
    create_time,
    report_type,
    requested_date,
    campus_id,
    requested_by,
    status,
    progress,
    file_id,
    expires_at,
    retry_count
)
VALUES
    (
        '19200000-0000-4000-8000-000000000001',
        'admin',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        'TEACHING_QUALITY_WEEKLY',
        CURRENT_DATE - 1,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC'),
        'admin',
        'SUCCEEDED',
        100,
        (SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1),
        CURRENT_TIMESTAMP + INTERVAL '30 day',
        0
    ),
    (
        '19200000-0000-4000-8000-000000000002',
        'admin',
        CURRENT_TIMESTAMP,
        'GRADE_ANALYSIS',
        CURRENT_DATE,
        (SELECT id FROM t_organization WHERE org_code = 'EDU-NCVC-MAIN'),
        'admin',
        'RUNNING',
        45,
        NULL,
        NULL,
        0
    )
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 8. 集成中心
-- ---------------------------------------------------------------------------

INSERT INTO int_connector (
    id,
    create_by,
    create_time,
    name,
    type,
    base_url,
    status,
    timeout_ms,
    max_retries,
    config_json
)
VALUES
    (
        '20000000-0000-4000-8000-000000000001',
        'admin',
        CURRENT_TIMESTAMP,
        '省级学籍系统接口',
        'HTTP',
        'https://example.edu.cn/student-api',
        'DISABLED',
        5000,
        3,
        '{"authType":"API_KEY","environment":"SHOWCASE","readOnly":true}'
    ),
    (
        '20000000-0000-4000-8000-000000000002',
        'admin',
        CURRENT_TIMESTAMP,
        '企业微信消息通道预留',
        'HTTP',
        'https://qyapi.weixin.qq.com',
        'DISABLED',
        5000,
        3,
        '{"environment":"SHOWCASE","configured":false}'
    )
ON CONFLICT DO NOTHING;

INSERT INTO int_sync_job (
    id,
    create_by,
    create_time,
    name,
    connector_id,
    cron_expression,
    status,
    request_path,
    idempotency_key_template
)
VALUES
    (
        '20100000-0000-4000-8000-000000000001',
        'admin',
        CURRENT_TIMESTAMP,
        '每日同步在籍学生变更',
        '20000000-0000-4000-8000-000000000001',
        '0 0 2 * * ?',
        'DISABLED',
        '/v1/students/changes',
        'student-sync-{runId}'
    ),
    (
        '20100000-0000-4000-8000-000000000002',
        'admin',
        CURRENT_TIMESTAMP,
        '企业微信通知同步',
        '20000000-0000-4000-8000-000000000002',
        '0 */10 * * * ?',
        'DISABLED',
        '/cgi-bin/message/send',
        'message-sync-{runId}'
    )
ON CONFLICT DO NOTHING;

-- 展示连接器只保留配置和历史运行记录，不允许调度器请求虚构域名。
UPDATE int_connector
SET status = 'DISABLED'
WHERE id IN (
    '20000000-0000-4000-8000-000000000001',
    '20000000-0000-4000-8000-000000000002'
);

UPDATE int_sync_job
SET status = 'DISABLED'
WHERE id IN (
    '20100000-0000-4000-8000-000000000001',
    '20100000-0000-4000-8000-000000000002'
);

INSERT INTO int_sync_run (
    id,
    create_by,
    create_time,
    job_id,
    status,
    started_at,
    finished_at,
    success_count,
    failure_count,
    attempt_count,
    error_message
)
VALUES
    (
        '20200000-0000-4000-8000-000000000001',
        'SYSTEM',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        '20100000-0000-4000-8000-000000000001',
        'SUCCEEDED',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        CURRENT_TIMESTAMP - INTERVAL '1 day' + INTERVAL '40 second',
        12,
        0,
        1,
        NULL
    ),
    (
        '20200000-0000-4000-8000-000000000002',
        'SYSTEM',
        CURRENT_TIMESTAMP - INTERVAL '2 day',
        '20100000-0000-4000-8000-000000000001',
        'FAILED',
        CURRENT_TIMESTAMP - INTERVAL '2 day',
        CURRENT_TIMESTAMP - INTERVAL '2 day' + INTERVAL '1 minute',
        8,
        2,
        3,
        '外部演示地址不可访问，保留失败状态用于页面展示'
    )
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 9. 通知公告渠道、模板、偏好与投递状态
-- ---------------------------------------------------------------------------

INSERT INTO msg_channel_policy (
    id,
    create_by,
    create_time,
    channel,
    enabled,
    max_per_day,
    max_per_minute,
    min_interval_seconds
)
VALUES
    ('21000000-0000-4000-8000-000000000001', 'admin', CURRENT_TIMESTAMP, 'IN_APP', true, 200, 20, 1),
    ('21000000-0000-4000-8000-000000000002', 'admin', CURRENT_TIMESTAMP, 'EMAIL', true, 50, 5, 10),
    ('21000000-0000-4000-8000-000000000003', 'admin', CURRENT_TIMESTAMP, 'SMS', false, 10, 2, 60),
    ('21000000-0000-4000-8000-000000000004', 'admin', CURRENT_TIMESTAMP, 'WECHAT_WORK', false, 100, 10, 5)
ON CONFLICT DO NOTHING;

INSERT INTO msg_channel_preference (
    id,
    create_by,
    create_time,
    channel,
    daily_limit,
    enabled,
    quiet_end,
    quiet_start,
    username
)
VALUES
    ('21100000-0000-4000-8000-000000000001', 'admin', CURRENT_TIMESTAMP, 'IN_APP', 100, true, TIME '07:00', TIME '22:00', 'admin'),
    ('21100000-0000-4000-8000-000000000002', 'admin', CURRENT_TIMESTAMP, 'EMAIL', 20, true, TIME '07:00', TIME '22:00', 'admin'),
    ('21100000-0000-4000-8000-000000000003', 'teacher.demo', CURRENT_TIMESTAMP, 'IN_APP', 100, true, TIME '07:30', TIME '21:30', 'teacher.demo'),
    ('21100000-0000-4000-8000-000000000004', 'teacher.demo', CURRENT_TIMESTAMP, 'EMAIL', 10, false, TIME '07:30', TIME '21:30', 'teacher.demo')
ON CONFLICT DO NOTHING;

INSERT INTO msg_notification_template (
    id,
    create_by,
    create_time,
    channel,
    content_template,
    enabled,
    subject_template,
    template_code,
    template_name
)
VALUES
    (
        '21200000-0000-4000-8000-000000000001',
        'admin',
        CURRENT_TIMESTAMP,
        'IN_APP',
        '您有一项待办：${title}，请于 ${deadline} 前处理。',
        true,
        '流程待办提醒',
        'WORKFLOW_TASK_REMINDER',
        '流程待办站内提醒'
    ),
    (
        '21200000-0000-4000-8000-000000000002',
        'admin',
        CURRENT_TIMESTAMP,
        'EMAIL',
        '您好，${studentName} 的 ${courseName} 成绩已发布，请登录门户查看。',
        true,
        '成绩发布通知',
        'GRADE_PUBLISHED',
        '成绩发布邮件模板'
    ),
    (
        '21200000-0000-4000-8000-000000000003',
        'admin',
        CURRENT_TIMESTAMP,
        'SMS',
        '【南城职校】${title}，请登录平台查看。',
        false,
        NULL,
        'GENERAL_SMS',
        '通用短信模板预留'
    )
ON CONFLICT DO NOTHING;

INSERT INTO msg_publication_delivery (
    id,
    create_by,
    create_time,
    attempt_count,
    channel,
    delivered_at,
    last_error,
    next_attempt_at,
    publication_id,
    status,
    username
)
SELECT
    md5('showcase-publication-delivery-' || publication.id || '-' || user_name || '-IN_APP')::uuid::text,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    1,
    'IN_APP',
    publication.published_at,
    NULL,
    NULL,
    publication.id,
    'DELIVERED',
    user_name
FROM msg_publication publication
CROSS JOIN (VALUES ('admin'), ('teacher.demo'), ('edu.reviewer.qa')) users(user_name)
WHERE publication.status = 'PUBLISHED'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 10. 岗位职务、门户偏好和流程治理展示
-- ---------------------------------------------------------------------------

INSERT INTO t_job_title (
    id,
    create_by,
    create_time,
    sort_order,
    status,
    title_code,
    title_level,
    title_name,
    title_type
)
VALUES
    ('22000000-0000-4000-8000-000000000001', 'admin', CURRENT_TIMESTAMP, 10, 1, 'EDU-SENIOR-TEACHER', 'SENIOR', '高级讲师', 'TEACHING'),
    ('22000000-0000-4000-8000-000000000002', 'admin', CURRENT_TIMESTAMP, 20, 1, 'EDU-LECTURER', 'MIDDLE', '讲师', 'TEACHING'),
    ('22000000-0000-4000-8000-000000000003', 'admin', CURRENT_TIMESTAMP, 30, 1, 'EDU-ASSISTANT', 'JUNIOR', '助理讲师', 'TEACHING'),
    ('22000000-0000-4000-8000-000000000004', 'admin', CURRENT_TIMESTAMP, 40, 1, 'EDU-SENIOR-TECHNICIAN', 'SENIOR', '高级实习指导教师', 'PRACTICE')
ON CONFLICT DO NOTHING;

INSERT INTO t_resource (
    id,
    create_by,
    create_time,
    entry_date,
    is_manager,
    organization_unit_id,
    position,
    position_level,
    resource_code,
    resource_name,
    user_id
)
SELECT
    md5('showcase-resource-' || user_account.id)::uuid::text,
    'showcase_seed',
    CURRENT_TIMESTAMP,
    '2026-08-20',
    user_account.username IN ('admin', 'edu.reviewer.qa'),
    COALESCE(employee_assignment.organization_unit_id, (
        SELECT id FROM t_organization_unit ORDER BY sort_order LIMIT 1
    )),
    COALESCE(user_account.position_name, '教师'),
    'MIDDLE',
    'RES-' || upper(replace(user_account.username, '.', '-')),
    COALESCE(user_account.display_name, user_account.username),
    user_account.id
FROM t_admin_user user_account
LEFT JOIN t_employee_assignment employee_assignment
    ON employee_assignment.employee_id = user_account.employee_id
WHERE user_account.username IN ('admin', 'teacher.demo', 'edu.reviewer.qa')
ON CONFLICT DO NOTHING;

INSERT INTO t_user_portal_preference (
    id,
    create_by,
    create_time,
    layout_json,
    theme,
    username
)
VALUES
    (
        '22100000-0000-4000-8000-000000000001',
        'admin',
        CURRENT_TIMESTAMP,
        '{"widgets":["todo","supervision","publications","data-quality"],"columns":3}',
        'LIGHT',
        'admin'
    ),
    (
        '22100000-0000-4000-8000-000000000002',
        'teacher.demo',
        CURRENT_TIMESTAMP,
        '{"widgets":["schedule","todo","homework","meetings"],"columns":2}',
        'LIGHT',
        'teacher.demo'
    )
ON CONFLICT DO NOTHING;

INSERT INTO wf_ai_setting (
    id,
    create_by,
    create_time,
    allow_external,
    enabled,
    mask_sensitive_data,
    provider_mode
)
VALUES (
    '22200000-0000-4000-8000-000000000001',
    'admin',
    CURRENT_TIMESTAMP,
    false,
    true,
    true,
    'PLATFORM_MODEL'
)
ON CONFLICT DO NOTHING;

INSERT INTO wf_delegation (
    id,
    create_by,
    create_time,
    definition_id,
    delegatee,
    delegator,
    enabled,
    start_at,
    end_at,
    reason
)
VALUES (
    '22300000-0000-4000-8000-000000000001',
    'teacher.demo',
    CURRENT_TIMESTAMP,
    (SELECT id FROM wf_definition WHERE flow_code = 'EDU_TEACHER_LEAVE_APPROVAL'),
    'edu.reviewer.qa',
    'teacher.demo',
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP + INTERVAL '7 day',
    '外出参加技能竞赛期间委托处理教学审批事项'
)
ON CONFLICT DO NOTHING;

INSERT INTO wf_execution_log (
    id,
    create_by,
    create_time,
    duration_ms,
    engine_instance_id,
    error_message,
    executor,
    finished_at,
    instance_id,
    node_id,
    node_key,
    request_json,
    response_json,
    started_at,
    status
)
SELECT
    '22400000-0000-4000-8000-000000000001',
    'SYSTEM',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    328,
    instance.engine_instance_id,
    NULL,
    'HTTP',
    CURRENT_TIMESTAMP - INTERVAL '1 day' + INTERVAL '328 millisecond',
    instance.id,
    COALESCE(node.id, 'showcase-node'),
    COALESCE(node.node_key, 'notify'),
    '{"event":"APPROVED","recipient":"teacher.demo"}',
    '{"success":true,"messageId":"showcase-message-001"}',
    CURRENT_TIMESTAMP - INTERVAL '1 day',
    'SUCCEEDED'
FROM wf_instance instance
LEFT JOIN LATERAL (
    SELECT id, node_key
    FROM wf_node
    WHERE flow_id = instance.definition_id
    ORDER BY create_time
    LIMIT 1
) node ON true
ORDER BY instance.create_time DESC
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO wf_incident (
    id,
    create_by,
    create_time,
    context_json,
    engine_instance_id,
    engine_job_id,
    error_message,
    execution_id,
    incident_type,
    instance_id,
    node_key,
    resolution,
    resolved_at,
    resolved_by,
    retry_count,
    status
)
SELECT
    '22500000-0000-4000-8000-000000000001',
    'SYSTEM',
    CURRENT_TIMESTAMP - INTERVAL '6 day',
    '{"showcase":true,"operation":"SEND_NOTIFICATION"}',
    instance.engine_instance_id,
    'showcase-engine-job-resolved-001',
    '消息通道首次调用超时',
    'showcase-execution-001',
    'AUTOMATIC_NODE_FAILURE',
    instance.id,
    'notify',
    'RETRY_SUCCESS',
    CURRENT_TIMESTAMP - INTERVAL '5 day',
    'admin',
    1,
    'RESOLVED'
FROM wf_instance instance
ORDER BY instance.create_time DESC
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO wf_review (
    id,
    create_by,
    create_time,
    blocking,
    category,
    config_hash,
    description,
    flow_id,
    node_id,
    severity,
    source,
    suggestion,
    title
)
SELECT
    '22600000-0000-4000-8000-000000000001',
    'admin',
    CURRENT_TIMESTAMP,
    false,
    'QUALITY',
    md5(definition.config_json::text),
    '流程已配置开始、审批和结束节点，候选人范围有效。',
    definition.id,
    NULL,
    'INFO',
    'SHOWCASE_SEED',
    '发布前继续执行一次普通用户权限验证。',
    '教师请假流程发布检查'
FROM wf_definition definition
WHERE definition.flow_code = 'EDU_TEACHER_LEAVE_APPROVAL'
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------------------
-- 11. 错题复核、题目引用、消息附件、死信和行业安装状态
-- ---------------------------------------------------------------------------

INSERT INTO edu_error_review (
    id,
    error_item_id,
    reviewer_id,
    status,
    note,
    reviewed_at
)
SELECT
    '23000000-0000-4000-8000-000000000001',
    error_item.id,
    teacher.id,
    'CONFIRMED',
    '已确认错因属于基础概念混淆，建议完成同类题强化训练。',
    CURRENT_TIMESTAMP - INTERVAL '2 day'
FROM edu_error_item error_item
CROSS JOIN LATERAL (
    SELECT id FROM edu_teacher_profile ORDER BY teacher_no LIMIT 1
) teacher
ORDER BY error_item.id
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_question_file (
    question_id,
    file_id
)
SELECT
    question.id,
    managed_file.id
FROM edu_question question
CROSS JOIN LATERAL (
    SELECT id
    FROM t_managed_file
    WHERE status = 'ACTIVE'
    ORDER BY create_time
    LIMIT 1
) managed_file
ORDER BY question.create_time
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO edu_question_reference (
    id,
    question_id,
    version_id,
    consumer_type,
    consumer_id,
    create_time
)
SELECT
    '23100000-0000-4000-8000-000000000001',
    version.question_id,
    version.id,
    'HOMEWORK',
    assignment.id,
    CURRENT_TIMESTAMP - INTERVAL '1 day'
FROM edu_question_version version
CROSS JOIN LATERAL (
    SELECT id FROM edu_homework_assignment ORDER BY create_time DESC LIMIT 1
) assignment
ORDER BY version.create_time DESC
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO msg_publication_attachment (
    id,
    create_by,
    create_time,
    content_type,
    file_size,
    original_name,
    primary_content,
    publication_id,
    sha256,
    storage_key
)
SELECT
    '23200000-0000-4000-8000-000000000001',
    'admin',
    CURRENT_TIMESTAMP,
    managed_file.content_type,
    managed_file.file_size,
    managed_file.original_name,
    false,
    publication.id,
    managed_file.sha256,
    managed_file.storage_key
FROM msg_publication publication
CROSS JOIN LATERAL (
    SELECT content_type, file_size, original_name, sha256, storage_key
    FROM t_managed_file
    WHERE status = 'ACTIVE'
    ORDER BY create_time
    LIMIT 1
) managed_file
WHERE publication.status = 'PUBLISHED'
ORDER BY publication.published_at DESC
LIMIT 1
ON CONFLICT DO NOTHING;

INSERT INTO int_sync_item_error (
    id,
    create_by,
    create_time,
    run_id,
    item_key,
    payload_json,
    error_message,
    status,
    attempt_count
)
VALUES (
    '23300000-0000-4000-8000-000000000001',
    'SYSTEM',
    CURRENT_TIMESTAMP - INTERVAL '2 day',
    '20200000-0000-4000-8000-000000000002',
    'student-20260021',
    '{"studentNo":"20260021","operation":"UPDATE"}',
    '外部演示地址连接超时',
    'DEAD_LETTER',
    3
)
ON CONFLICT DO NOTHING;

INSERT INTO int_dead_letter (
    id,
    create_by,
    create_time,
    item_error_id,
    run_id,
    replay_count,
    status,
    last_result,
    replayed_at
)
VALUES (
    '23400000-0000-4000-8000-000000000001',
    'SYSTEM',
    CURRENT_TIMESTAMP - INTERVAL '2 day',
    '23300000-0000-4000-8000-000000000001',
    '20200000-0000-4000-8000-000000000002',
    0,
    'PENDING',
    '等待管理员手工重放',
    NULL
)
ON CONFLICT DO NOTHING;

INSERT INTO edu_domain_outbox (
    id,
    create_by,
    create_time,
    event_id,
    event_type,
    aggregate_id,
    payload_json,
    actor,
    status,
    attempts,
    next_attempt_at,
    processed_at,
    last_error
)
VALUES
    (
        '23500000-0000-4000-8000-000000000001',
        'SYSTEM',
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        'showcase-grade-event-processed',
        'GRADEBOOK_PUBLISHED',
        (SELECT id FROM edu_gradebook ORDER BY create_time LIMIT 1),
        '{"showcase":true,"result":"projected"}',
        'edu.reviewer.qa',
        'PROCESSED',
        1,
        CURRENT_TIMESTAMP - INTERVAL '1 day',
        CURRENT_TIMESTAMP - INTERVAL '1 day' + INTERVAL '2 second',
        NULL
    ),
    (
        '23500000-0000-4000-8000-000000000002',
        'SYSTEM',
        CURRENT_TIMESTAMP,
        'showcase-homework-event-dead',
        'HomeworkGradesPublishedV1',
        (SELECT id FROM edu_homework_assignment ORDER BY create_time DESC LIMIT 1),
        '{"showcase":true,"result":"waiting"}',
        'teacher.demo',
        'DEAD',
        8,
        CURRENT_TIMESTAMP + INTERVAL '5 minute',
        NULL,
        '展示环境死信案例：外部成绩事件载荷不完整，禁止自动重试'
    )
ON CONFLICT DO NOTHING;

-- 已执行过旧版展示脚本时，将错误的待处理事件转换为可查看但不会自动重试的死信。
UPDATE edu_domain_outbox
SET event_id = 'showcase-homework-event-dead',
    event_type = 'HomeworkGradesPublishedV1',
    status = 'DEAD',
    attempts = 8,
    last_error = '展示环境死信案例：外部成绩事件载荷不完整，禁止自动重试',
    next_attempt_at = CURRENT_TIMESTAMP,
    row_version = row_version + 1
WHERE id = '23500000-0000-4000-8000-000000000002';

INSERT INTO sys_industry_template_installation (
    id,
    create_by,
    create_time,
    active,
    industry_code,
    installed_at,
    installed_version
)
VALUES (
    '23600000-0000-4000-8000-000000000001',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    true,
    'EDUCATION',
    CURRENT_TIMESTAMP,
    '2026.09-showcase'
)
ON CONFLICT DO NOTHING;

-- 修正早期执行时可能引用到已删除文件的展示记录。
UPDATE edu_preparation_material
SET file_id = (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
)
WHERE id = '13200000-0000-4000-8000-000000000001';

UPDATE edu_courseware_version
SET file_id = (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
)
WHERE id = '13400000-0000-4000-8000-000000000001';

UPDATE edu_teaching_center_resource
SET file_id = (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
)
WHERE id = '13500000-0000-4000-8000-000000000001';

UPDATE edu_research_material
SET file_id = (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
)
WHERE id = '13800000-0000-4000-8000-000000000001';

UPDATE edu_research_result
SET file_id = (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
)
WHERE id = '13900000-0000-4000-8000-000000000001';

UPDATE edu_meeting_material
SET file_id = (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
)
WHERE id = '16200000-0000-4000-8000-000000000001';

UPDATE data_report_task
SET file_id = (
    SELECT id FROM t_managed_file WHERE status = 'ACTIVE' ORDER BY create_time LIMIT 1
)
WHERE id = '19200000-0000-4000-8000-000000000001';

COMMIT;

-- 执行后可使用以下查询快速确认覆盖情况：
-- SELECT relname, n_live_tup FROM pg_stat_user_tables
-- WHERE relname LIKE 'edu_%' OR relname LIKE 'data_%'
-- ORDER BY relname;
