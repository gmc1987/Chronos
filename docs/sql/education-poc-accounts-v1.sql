-- 教育 PoC 普通角色账号。密码哈希复制自现有 admin，避免脚本内保存明文密码。
-- 账号用于本地 PoC 自动验收，不强制首次改密；生产部署不得执行本脚本。
BEGIN;

WITH account_source AS (
    SELECT password
    FROM t_admin_user
    WHERE username = 'admin'
), teacher_source AS (
    SELECT employee_id, teacher_name,
           row_number() OVER (ORDER BY teacher_no) AS row_no
    FROM edu_teacher_profile
    WHERE enabled = true
), desired AS (
    SELECT 'teacher.demo' AS username, employee_id, teacher_name AS display_name
    FROM teacher_source WHERE row_no = 1
    UNION ALL
    SELECT 'academic.demo', employee_id, teacher_name
    FROM teacher_source WHERE row_no = 2
)
INSERT INTO t_admin_user (
    id, create_by, create_time, username, password, display_name,
    employee_id, status, account_locked, account_type,
    failed_login_attempts, must_change_password, token_version
)
SELECT
    gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
    desired.username, account_source.password, desired.display_name,
    desired.employee_id, 1, false, 'ADMIN', 0, false, 0
FROM desired
CROSS JOIN account_source
ON CONFLICT (username) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    status = 1,
    account_locked = false,
    must_change_password = false,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

WITH account_source AS (
    SELECT password
    FROM t_admin_user
    WHERE username = 'admin'
), desired AS (
    SELECT
        'student.demo' AS username,
        student_name AS display_name,
        'STUDENT' AS account_type
    FROM edu_student_profile
    WHERE enrollment_status = 'ACTIVE'
    ORDER BY student_no
    LIMIT 1
), parent_account AS (
    SELECT
        'parent.demo' AS username,
        parent_name AS display_name,
        'PARENT' AS account_type
    FROM edu_parent_profile
    WHERE status = 'ACTIVE'
    ORDER BY parent_no
    LIMIT 1
), all_accounts AS (
    SELECT * FROM desired
    UNION ALL
    SELECT * FROM parent_account
)
INSERT INTO t_admin_user (
    id, create_by, create_time, username, password, display_name,
    status, account_locked, account_type,
    failed_login_attempts, must_change_password, token_version
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    all_accounts.username,
    account_source.password,
    all_accounts.display_name,
    1,
    false,
    all_accounts.account_type,
    0,
    false,
    0
FROM all_accounts
CROSS JOIN account_source
ON CONFLICT (username) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    account_type = EXCLUDED.account_type,
    status = 1,
    account_locked = false,
    must_change_password = false,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO edu_user_profile_binding (
    id, username, profile_type, profile_id, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    source.username,
    source.profile_type,
    source.profile_id,
    'ACTIVE',
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
FROM (
    SELECT
        'teacher.demo' AS username,
        'TEACHER' AS profile_type,
        (SELECT id FROM edu_teacher_profile ORDER BY teacher_no LIMIT 1) AS profile_id
    UNION ALL
    SELECT
        'academic.demo',
        'TEACHER',
        (SELECT id FROM edu_teacher_profile ORDER BY teacher_no OFFSET 1 LIMIT 1)
    UNION ALL
    SELECT
        'student.demo',
        'STUDENT',
        (SELECT id FROM edu_student_profile WHERE enrollment_status = 'ACTIVE' ORDER BY student_no LIMIT 1)
    UNION ALL
    SELECT
        'parent.demo',
        'PARENT',
        (SELECT id FROM edu_parent_profile WHERE status = 'ACTIVE' ORDER BY parent_no LIMIT 1)
) source
WHERE source.profile_id IS NOT NULL
ON CONFLICT (username, profile_type) DO UPDATE SET
    profile_id = EXCLUDED.profile_id,
    status = 'ACTIVE',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO t_user_role (user_id, role_id)
SELECT account.id, role.id
FROM t_admin_user account
JOIN t_role role ON role.role_code IN ('ROLE_PLATFORM_USER', 'EDU_CLASS_ADVISOR')
WHERE account.username = 'teacher.demo'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO t_user_role (user_id, role_id)
SELECT account.id, role.id
FROM t_admin_user account
JOIN t_role role ON role.role_code IN ('ROLE_PLATFORM_USER', 'EDU_ACADEMIC_APPROVER')
WHERE account.username = 'academic.demo'
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO t_user_role (user_id, role_id)
SELECT account.id, role.id
FROM t_admin_user account
JOIN t_role role ON role.role_code = 'ROLE_PLATFORM_USER'
WHERE account.username IN ('student.demo', 'parent.demo')
ON CONFLICT (user_id, role_id) DO NOTHING;

COMMIT;
