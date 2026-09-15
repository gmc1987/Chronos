-- 考试中心生产闭环验证账号。统一密码：ChronosExam@2026
-- 数据故意保留，便于产品验收；重复执行不会覆盖已有账号或密码。
WITH accounts(username, display_name) AS (
    VALUES
        ('exam.manager.demo', '考试中心测试考务员'),
        ('exam.approver.demo', '考试中心测试审批员')
)
INSERT INTO t_admin_user (
    id, create_by, create_time, username, password, display_name,
    account_type, status, account_locked, failed_login_attempts,
    must_change_password, token_version)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       account.username,
       '$2y$10$x/5YKiXBT12xr7Hn7qwwkevrIegDAQzqA7wHfHwajyhD3yhmiHw1.',
       account.display_name, 'ADMIN', 1, false, 0, false, 0
FROM accounts account
WHERE NOT EXISTS (
    SELECT 1 FROM t_admin_user existing
    WHERE existing.username = account.username
);

-- 两个账号均具备完整数据范围；业务代码仍强制禁止申请人自审。
INSERT INTO t_user_role (user_id, role_id)
SELECT account.id, role.id
FROM t_admin_user account
JOIN t_role role ON role.role_code = 'SUPER_ADMIN'
WHERE account.username IN ('exam.manager.demo', 'exam.approver.demo')
ON CONFLICT DO NOTHING;

-- 为当前学期两个校区补齐考试冲突计算所需的标准作息。
INSERT INTO edu_bell_schedule (
    id, create_by, create_time, academic_term_id, campus_id,
    schedule_code, schedule_name, default_schedule, status)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       term.id, classroom.campus_id,
       'EXAM-DEMO-' || right(classroom.campus_id, 4),
       '考试中心验证标准作息', true, 'ACTIVE'
FROM edu_academic_term term
CROSS JOIN (
    SELECT DISTINCT campus_id FROM edu_classroom
) classroom
WHERE term.term_code = '2026-2027-1'
  AND NOT EXISTS (
      SELECT 1 FROM edu_bell_schedule existing
      WHERE existing.academic_term_id = term.id
        AND existing.campus_id = classroom.campus_id
  );

INSERT INTO edu_bell_period (
    id, create_by, create_time, bell_schedule_id, period_no,
    period_name, start_time, end_time, day_segment, schedulable)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
       schedule.id, period.period_no, period.period_name,
       period.start_time::time, period.end_time::time,
       period.day_segment, true
FROM edu_bell_schedule schedule
JOIN edu_academic_term term ON term.id = schedule.academic_term_id
CROSS JOIN (VALUES
    (1, '第一节', '08:00', '08:45', 'MORNING'),
    (2, '第二节', '08:55', '09:40', 'MORNING'),
    (3, '第三节', '10:00', '10:45', 'MORNING'),
    (4, '第四节', '10:55', '11:40', 'MORNING'),
    (5, '第五节', '14:00', '14:45', 'AFTERNOON'),
    (6, '第六节', '14:55', '15:40', 'AFTERNOON'),
    (7, '第七节', '16:00', '16:45', 'AFTERNOON'),
    (8, '第八节', '16:55', '17:40', 'AFTERNOON')
) AS period(period_no, period_name, start_time, end_time, day_segment)
WHERE term.term_code = '2026-2027-1'
  AND NOT EXISTS (
      SELECT 1 FROM edu_bell_period existing
      WHERE existing.bell_schedule_id = schedule.id
        AND existing.period_no = period.period_no
  );
