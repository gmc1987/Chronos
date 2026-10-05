-- Chronos 教育演示基础数据
-- 目标数据库：ChronosEducation（PostgreSQL）
-- 用途：本地/非生产功能演示。严禁在生产数据库执行。
--
-- 数据规模：
--   1 所学校、3 个行政部门、4 个年级组织单元、10 个专业组织单元
--   4 个年级、10 个专业、40 个行政班、每班 50 名学生（共 2,000 名）
--   50 名教师（40 名班主任 + 10 名专业负责人）
--   50 间教学/实训教室
--   4,052 个演示账号（管理员、教务、教师、学生、家长）
--
-- 前置条件：已完成 education-app 的 Flyway 迁移。
-- 执行方式：
--   psql -v ON_ERROR_STOP=1 -U Chronos -d ChronosEducation \
--     -f Chronos/scripts/education-demo-foundation-data.sql
--
-- 统一演示密码：ChronosDemo@2026
-- 密码仅用于本地演示，生产环境不得复用。

BEGIN;

SELECT pg_advisory_xact_lock(hashtext('chronos-education-demo-foundation-v1'));
SET LOCAL TIME ZONE 'Asia/Shanghai';

-- ---------------------------------------------------------------------------
-- 1. 学校与组织架构
-- ---------------------------------------------------------------------------

INSERT INTO t_organization (
    id, create_by, create_time, org_code, organization_name,
    organization_type, register_time, short_name, sort_order, status,
    timezone, description
)
VALUES (
    md5('chronos-demo-school-001')::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, 'DEMO-SCHOOL-001',
    '南城现代职业技术学校（演示校）', 'SCHOOL', CURRENT_TIMESTAMP,
    '南城职校演示校', 1, 1, 'Asia/Shanghai',
    'Chronos 教育平台功能演示学校'
)
ON CONFLICT (org_code) DO UPDATE
SET organization_name = EXCLUDED.organization_name,
    short_name = EXCLUDED.short_name,
    status = EXCLUDED.status,
    timezone = EXCLUDED.timezone,
    description = EXCLUDED.description,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

-- 先建立学校直属组织单元，后续年级和专业单元挂到教学中心。
WITH school AS (
    SELECT id
    FROM t_organization
    WHERE org_code = 'DEMO-SCHOOL-001'
), units(unit_code, unit_name, unit_type, sort_order) AS (
    VALUES
        ('DEMO-UNIT-ACADEMIC', '教务处', 'ADMINISTRATION', 10),
        ('DEMO-UNIT-STUDENT-AFFAIRS', '学生处', 'ADMINISTRATION', 20),
        ('DEMO-UNIT-TEACHING', '教学中心', 'TEACHING', 30)
)
INSERT INTO t_organization_unit (
    id, create_by, create_time, org_id, organization_unit_code,
    organization_unit_name, level, sort_order, status, unit_type, tree_path
)
SELECT
    md5('chronos-demo-unit-' || units.unit_code)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, school.id, units.unit_code,
    units.unit_name, 1, units.sort_order, 1, units.unit_type,
    '/' || units.unit_code
FROM school
CROSS JOIN units
WHERE NOT EXISTS (
    SELECT 1
    FROM t_organization_unit existing
    WHERE existing.organization_unit_code = units.unit_code
);

-- 四个年级组织单元。
WITH school AS (
    SELECT id
    FROM t_organization
    WHERE org_code = 'DEMO-SCHOOL-001'
), teaching AS (
    SELECT id
    FROM t_organization_unit
    WHERE organization_unit_code = 'DEMO-UNIT-TEACHING'
), grades(grade_no, grade_year) AS (
    VALUES (1, 2023), (2, 2024), (3, 2025), (4, 2026)
)
INSERT INTO t_organization_unit (
    id, create_by, create_time, org_id, organization_unit_code,
    organization_unit_name, level, sort_order, status, unit_type,
    parent_organization_unit_id, tree_path
)
SELECT
    md5('chronos-demo-unit-grade-' || grades.grade_no)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, school.id,
    'DEMO-GRADE-' || grades.grade_no,
    grades.grade_year || '级', 2, 100 + grades.grade_no, 1, 'GRADE',
    teaching.id, '/DEMO-UNIT-TEACHING/DEMO-GRADE-' || grades.grade_no
FROM school
CROSS JOIN teaching
CROSS JOIN grades
WHERE NOT EXISTS (
    SELECT 1
    FROM t_organization_unit existing
    WHERE existing.organization_unit_code = 'DEMO-GRADE-' || grades.grade_no
);

-- 十个专业组织单元。
WITH school AS (
    SELECT id
    FROM t_organization
    WHERE org_code = 'DEMO-SCHOOL-001'
), teaching AS (
    SELECT id
    FROM t_organization_unit
    WHERE organization_unit_code = 'DEMO-UNIT-TEACHING'
), majors(major_no, major_name) AS (
    VALUES
        (1, '计算机应用技术'),
        (2, '软件技术'),
        (3, '电子商务'),
        (4, '数字媒体技术'),
        (5, '机电一体化技术'),
        (6, '工业机器人技术'),
        (7, '汽车运用与维修'),
        (8, '建筑工程技术'),
        (9, '会计事务'),
        (10, '幼儿保育')
)
INSERT INTO t_organization_unit (
    id, create_by, create_time, org_id, organization_unit_code,
    organization_unit_name, level, sort_order, status, unit_type,
    parent_organization_unit_id, tree_path
)
SELECT
    md5('chronos-demo-unit-major-' || majors.major_no)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, school.id,
    'DEMO-MAJOR-' || lpad(majors.major_no::text, 2, '0'),
    majors.major_name, 2, 200 + majors.major_no, 1, 'MAJOR',
    teaching.id,
    '/DEMO-UNIT-TEACHING/DEMO-MAJOR-' || lpad(majors.major_no::text, 2, '0')
FROM school
CROSS JOIN teaching
CROSS JOIN majors
WHERE NOT EXISTS (
    SELECT 1
    FROM t_organization_unit existing
    WHERE existing.organization_unit_code =
        'DEMO-MAJOR-' || lpad(majors.major_no::text, 2, '0')
);

-- ---------------------------------------------------------------------------
-- 2. 年级与专业主数据
-- ---------------------------------------------------------------------------

WITH grades(grade_no, grade_year) AS (
    VALUES (1, 2023), (2, 2024), (3, 2025), (4, 2026)
)
INSERT INTO edu_grade (
    id, grade_code, grade_name, enrollment_year, education_stage,
    enabled, sort_order, create_by, create_time, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-grade-' || grades.grade_no)::uuid::text,
    'DEMO-GRADE-' || grades.grade_no,
    grades.grade_year || '级',
    grades.grade_year,
    'VOCATIONAL',
    true, grades.grade_no * 10, 'demo_seed', CURRENT_TIMESTAMP,
    'demo_seed', CURRENT_TIMESTAMP
FROM grades
WHERE NOT EXISTS (
    SELECT 1
    FROM edu_grade existing
    WHERE existing.grade_code = 'DEMO-GRADE-' || grades.grade_no
);

WITH majors(major_no, major_name) AS (
    VALUES
        (1, '计算机应用技术'),
        (2, '软件技术'),
        (3, '电子商务'),
        (4, '数字媒体技术'),
        (5, '机电一体化技术'),
        (6, '工业机器人技术'),
        (7, '汽车运用与维修'),
        (8, '建筑工程技术'),
        (9, '会计事务'),
        (10, '幼儿保育')
)
INSERT INTO edu_major (
    id, create_by, create_time, department_id, enabled,
    major_code, major_name, schooling_years,
    last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-major-' || majors.major_no)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP,
    unit.id, true,
    'DEMO-MAJOR-' || lpad(majors.major_no::text, 2, '0'),
    majors.major_name, 3,
    'demo_seed', CURRENT_TIMESTAMP
FROM majors
JOIN t_organization_unit unit
  ON unit.organization_unit_code =
      'DEMO-MAJOR-' || lpad(majors.major_no::text, 2, '0')
WHERE NOT EXISTS (
    SELECT 1
    FROM edu_major existing
    WHERE existing.major_code =
        'DEMO-MAJOR-' || lpad(majors.major_no::text, 2, '0')
);

-- ---------------------------------------------------------------------------
-- 3. 教职工与教师档案
--    40 名班主任（每班 1 名）+ 10 名专业负责人。
-- ---------------------------------------------------------------------------

WITH major_teachers AS (
    SELECT
        major_no,
        'DEMO-M' || lpad(major_no::text, 2, '0') AS teacher_no,
        'DEMO-EMP-M' || lpad(major_no::text, 2, '0') AS employee_code,
        '演示专业负责人-' || lpad(major_no::text, 2, '0') AS employee_name
    FROM generate_series(1, 10) AS major_no
), class_teachers AS (
    SELECT
        grade_no,
        class_no,
        'DEMO-G' || grade_no || '-C' || lpad(class_no::text, 2, '0') AS teacher_no,
        'DEMO-EMP-G' || grade_no || '-C' || lpad(class_no::text, 2, '0') AS employee_code,
        '演示班主任-' || (2022 + grade_no) || '级-' ||
            lpad(class_no::text, 2, '0') || '班' AS employee_name
    FROM generate_series(1, 4) AS grade_no
    CROSS JOIN generate_series(1, 10) AS class_no
), employees AS (
    SELECT teacher_no, employee_code, employee_name, major_no
    FROM major_teachers
    UNION ALL
    SELECT class_teachers.teacher_no, class_teachers.employee_code,
           class_teachers.employee_name, ((class_teachers.class_no - 1) % 10) + 1
    FROM class_teachers
)
INSERT INTO t_iam_employee (
    id, create_by, create_time, employee_code, employee_name,
    employee_type, employment_status, gender, hire_date, phone, email,
    last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-employee-' || employees.employee_code)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, employees.employee_code,
    employees.employee_name, 'TEACHER', 'ACTIVE',
    CASE WHEN right(employees.employee_code, 1)::integer % 2 = 0
         THEN 'FEMALE' ELSE 'MALE' END,
    DATE '2020-09-01',
    '159' || lpad(row_number() OVER (ORDER BY employees.employee_code)::text, 8, '0'),
    lower(employees.employee_code) || '@demo.chronos.local',
    'demo_seed', CURRENT_TIMESTAMP
FROM employees
ON CONFLICT (employee_code) DO UPDATE
SET employee_name = EXCLUDED.employee_name,
    employee_type = EXCLUDED.employee_type,
    employment_status = EXCLUDED.employment_status,
    phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

WITH major_teachers AS (
    SELECT
        major_no,
        'DEMO-M' || lpad(major_no::text, 2, '0') AS teacher_no
    FROM generate_series(1, 10) AS major_no
), class_teachers AS (
    SELECT
        grade_no,
        class_no,
        'DEMO-G' || grade_no || '-C' || lpad(class_no::text, 2, '0') AS teacher_no
    FROM generate_series(1, 4) AS grade_no
    CROSS JOIN generate_series(1, 10) AS class_no
), teachers AS (
    SELECT
        major_teachers.teacher_no,
        'DEMO-MAJOR-' || lpad(major_teachers.major_no::text, 2, '0')
            AS unit_code,
        'DEMO-MAJOR-' || lpad(major_teachers.major_no::text, 2, '0')
            AS major_code
    FROM major_teachers
    UNION ALL
    SELECT
        class_teachers.teacher_no,
        'DEMO-MAJOR-' ||
            lpad((((class_teachers.class_no - 1) % 10) + 1)::text, 2, '0'),
        'DEMO-MAJOR-' ||
            lpad((((class_teachers.class_no - 1) % 10) + 1)::text, 2, '0')
    FROM class_teachers
)
INSERT INTO edu_teacher_profile (
    id, create_by, create_time, department_id, employee_id, enabled,
    max_weekly_lessons, specialty, teacher_name, teacher_no,
    max_daily_lessons, max_consecutive_lessons, employment_status,
    last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-teacher-' || teachers.teacher_no)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, unit.id, employee.id, true,
    18,
    CASE WHEN teachers.teacher_no LIKE 'DEMO-M%'
         THEN '专业建设与课程负责人'
         ELSE '班级管理与专业教学'
    END,
    employee.employee_name, teachers.teacher_no,
    6, 4, 'ACTIVE',
    'demo_seed', CURRENT_TIMESTAMP
FROM teachers
JOIN t_iam_employee employee
  ON employee.employee_code =
      replace(teachers.teacher_no, 'DEMO-', 'DEMO-EMP-')
JOIN t_organization_unit unit
  ON unit.organization_unit_code = teachers.unit_code
WHERE NOT EXISTS (
    SELECT 1
    FROM edu_teacher_profile existing
    WHERE existing.teacher_no = teachers.teacher_no
);

-- ---------------------------------------------------------------------------
-- 4. 四个年级、十个专业、四十个行政班
--    每个年级 10 个班，按专业 1:1 对应。
-- ---------------------------------------------------------------------------

WITH class_data AS (
    SELECT
        grade_no,
        class_no,
        2022 + grade_no AS grade_year,
        'DEMO-GRADE-' || grade_no AS grade_code,
        'DEMO-MAJOR-' || lpad(class_no::text, 2, '0') AS major_code,
        'DEMO-G' || grade_no || '-C' || lpad(class_no::text, 2, '0') AS class_code
    FROM generate_series(1, 4) AS grade_no
    CROSS JOIN generate_series(1, 10) AS class_no
)
INSERT INTO edu_administrative_class (
    id, create_by, create_time, campus_id, class_code, class_name,
    grade_year, head_teacher_id, major_id, status, grade_id,
    last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-class-' || class_data.class_code)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, school.id,
    class_data.class_code,
    class_data.grade_year || '级' || major.major_name ||
        lpad(class_data.class_no::text, 2, '0') || '班',
    class_data.grade_year,
    teacher.id,
    major.id,
    'ACTIVE',
    grade.id,
    'demo_seed', CURRENT_TIMESTAMP
FROM class_data
JOIN t_organization school
  ON school.org_code = 'DEMO-SCHOOL-001'
JOIN edu_grade grade
  ON grade.grade_code = class_data.grade_code
JOIN edu_major major
  ON major.major_code = class_data.major_code
JOIN edu_teacher_profile teacher
  ON teacher.teacher_no =
      'DEMO-G' || class_data.grade_no || '-C' ||
      lpad(class_data.class_no::text, 2, '0')
ON CONFLICT (class_code) DO UPDATE
SET campus_id = EXCLUDED.campus_id,
    class_name = EXCLUDED.class_name,
    grade_year = EXCLUDED.grade_year,
    head_teacher_id = EXCLUDED.head_teacher_id,
    major_id = EXCLUDED.major_id,
    status = EXCLUDED.status,
    grade_id = EXCLUDED.grade_id,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

-- 以每个年级第一班班主任作为年级主任。
UPDATE edu_grade grade
SET director_teacher_id = teacher.id,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP
FROM edu_teacher_profile teacher
WHERE grade.grade_code = 'DEMO-GRADE-' ||
    substring(teacher.teacher_no FROM 7 FOR 1)
  AND teacher.teacher_no LIKE 'DEMO-G%-C01';

-- ---------------------------------------------------------------------------
-- 5. 教室资源：40 间班级教室 + 10 间专业实训室
-- ---------------------------------------------------------------------------

WITH standard_rooms AS (
    SELECT
        grade_no, class_no,
        'DEMO-G' || grade_no || '-C' || lpad(class_no::text, 2, '0') AS room_code,
        (2022 + grade_no) || '级' ||
            lpad(class_no::text, 2, '0') || '班教室' AS room_name,
        '教学楼' || grade_no AS building_name
    FROM generate_series(1, 4) AS grade_no
    CROSS JOIN generate_series(1, 10) AS class_no
), lab_rooms AS (
    SELECT
        lab_no,
        'DEMO-LAB-' || lpad(lab_no::text, 2, '0') AS room_code,
        '专业实训室-' || lpad(lab_no::text, 2, '0') AS room_name,
        CASE WHEN lab_no <= 5 THEN '综合实训楼' ELSE '专业实训楼' END
            AS building_name
    FROM generate_series(1, 10) AS lab_no
), rooms AS (
    SELECT room_code, room_name, building_name, 50 AS capacity, 'STANDARD' AS room_type
    FROM standard_rooms
    UNION ALL
    SELECT room_code, room_name, building_name, 60 AS capacity,
           CASE WHEN lab_no <= 5 THEN 'COMPUTER' ELSE 'LAB' END AS room_type
    FROM lab_rooms
)
INSERT INTO edu_classroom (
    id, room_code, room_name, campus_id, building_name, capacity,
    room_type, enabled, create_by, create_time, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-room-' || rooms.room_code)::uuid::text,
    rooms.room_code, rooms.room_name, school.id, rooms.building_name,
    rooms.capacity, rooms.room_type, true, 'demo_seed', CURRENT_TIMESTAMP,
    'demo_seed', CURRENT_TIMESTAMP
FROM rooms
JOIN t_organization school
  ON school.org_code = 'DEMO-SCHOOL-001'
ON CONFLICT (room_code) DO UPDATE
SET room_name = EXCLUDED.room_name,
    campus_id = EXCLUDED.campus_id,
    building_name = EXCLUDED.building_name,
    capacity = EXCLUDED.capacity,
    room_type = EXCLUDED.room_type,
    enabled = true,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 6. 学生、家长与监护关系
-- ---------------------------------------------------------------------------

WITH students AS (
    SELECT
        class_data.grade_no,
        class_data.class_no,
        student_no,
        'DEMO-' || class_data.class_code || '-S' ||
            lpad(student_no::text, 3, '0') AS student_code,
        class_data.class_code,
        class_data.grade_year,
        class_data.grade_code,
        class_data.major_code
    FROM (
        SELECT
            grade_no,
            class_no,
            2022 + grade_no AS grade_year,
            'DEMO-GRADE-' || grade_no AS grade_code,
            'DEMO-MAJOR-' || lpad(class_no::text, 2, '0') AS major_code,
            'DEMO-G' || grade_no || '-C' || lpad(class_no::text, 2, '0') AS class_code
        FROM generate_series(1, 4) AS grade_no
        CROSS JOIN generate_series(1, 10) AS class_no
    ) class_data
    CROSS JOIN generate_series(1, 50) AS student_no
)
INSERT INTO edu_student_profile (
    id, create_by, create_time, administrative_class_id,
    enrollment_status, gender, grade_year, major_id, phone,
    student_name, student_no, grade_id, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-student-' || students.student_code)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, class_data.id, 'ACTIVE',
    CASE WHEN students.student_no % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END,
    students.grade_year, major.id,
    '157' || lpad(
        ((students.grade_no - 1) * 500 +
         (students.class_no - 1) * 50 +
         students.student_no)::text, 8, '0'
    ),
    '演示学生-' || students.grade_year || '-' ||
        lpad(students.class_no::text, 2, '0') || '-' ||
        lpad(students.student_no::text, 3, '0'),
    students.student_code, grade.id,
    'demo_seed', CURRENT_TIMESTAMP
FROM students
JOIN edu_administrative_class class_data
  ON class_data.class_code = students.class_code
JOIN edu_grade grade
  ON grade.grade_code = students.grade_code
JOIN edu_major major
  ON major.major_code = students.major_code
ON CONFLICT (student_no) DO UPDATE
SET administrative_class_id = EXCLUDED.administrative_class_id,
    enrollment_status = 'ACTIVE',
    gender = EXCLUDED.gender,
    grade_year = EXCLUDED.grade_year,
    major_id = EXCLUDED.major_id,
    phone = EXCLUDED.phone,
    student_name = EXCLUDED.student_name,
    grade_id = EXCLUDED.grade_id,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO edu_parent_profile (
    id, parent_no, parent_name, gender, phone, employment, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-parent-' || student.student_no)::uuid::text,
    'DEMO-PARENT-' || student.student_no,
    student.student_name || '家长',
    CASE WHEN right(student.student_no, 1)::integer % 2 = 0
         THEN 'FEMALE' ELSE 'MALE' END,
    '158' || substring(student.phone FROM 4),
    '演示家长',
    'ACTIVE',
    'demo_seed', CURRENT_TIMESTAMP, 'demo_seed', CURRENT_TIMESTAMP
FROM edu_student_profile student
WHERE student.student_no LIKE 'DEMO-%'
ON CONFLICT (parent_no) DO UPDATE
SET parent_name = EXCLUDED.parent_name,
    gender = EXCLUDED.gender,
    phone = EXCLUDED.phone,
    status = 'ACTIVE',
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO edu_student_guardian (
    id, student_id, parent_id, relationship, primary_guardian,
    emergency_contact, create_by, create_time, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-guardian-' || student.student_no)::uuid::text,
    student.id, parent.id,
    CASE WHEN parent.gender = 'FEMALE' THEN 'MOTHER' ELSE 'FATHER' END,
    true, true, 'demo_seed', CURRENT_TIMESTAMP, 'demo_seed', CURRENT_TIMESTAMP
FROM edu_student_profile student
JOIN edu_parent_profile parent
  ON parent.parent_no = 'DEMO-PARENT-' || student.student_no
WHERE student.student_no LIKE 'DEMO-%'
ON CONFLICT (student_id, parent_id) DO UPDATE
SET relationship = EXCLUDED.relationship,
    primary_guardian = true,
    emergency_contact = true,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 7. 演示账号与账号-档案绑定
-- ---------------------------------------------------------------------------

-- BCrypt("ChronosDemo@2026"), 仅用于本地演示。
WITH school AS (
    SELECT id
    FROM t_organization
    WHERE org_code = 'DEMO-SCHOOL-001'
), accounts(username, display_name, account_type, employee_id) AS (
    VALUES
        ('demo.admin', '演示平台管理员', 'ADMIN', NULL),
        ('demo.registrar', '演示教务管理员', 'STAFF', NULL)
    UNION ALL
    SELECT
        CASE WHEN teacher.teacher_no LIKE 'DEMO-M%'
             THEN 'demo.teacher.major' ||
                  substring(teacher.teacher_no FROM 8 FOR 2)
             ELSE 'demo.teacher.g' ||
                  substring(teacher.teacher_no FROM 7 FOR 1) ||
                  'c' || substring(teacher.teacher_no FROM 10 FOR 2)
        END,
        teacher.teacher_name,
        'STAFF',
        teacher.employee_id
    FROM edu_teacher_profile teacher
    WHERE teacher.teacher_no LIKE 'DEMO-%'
    UNION ALL
    SELECT
        'demo.student.' || lower(student.student_no),
        student.student_name,
        'STUDENT',
        NULL
    FROM edu_student_profile student
    WHERE student.student_no LIKE 'DEMO-%'
    UNION ALL
    SELECT
        'demo.parent.' || lower(student.student_no),
        parent.parent_name,
        'PARENT',
        NULL
    FROM edu_student_profile student
    JOIN edu_parent_profile parent
      ON parent.parent_no = 'DEMO-PARENT-' || student.student_no
    WHERE student.student_no LIKE 'DEMO-%'
)
INSERT INTO t_admin_user (
    id, create_by, create_time, account_locked, account_type,
    display_name, employee_id, failed_login_attempts, must_change_password,
    organization_id, password, status, token_version, username,
    last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-account-' || accounts.username)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP, false, accounts.account_type,
    accounts.display_name, accounts.employee_id, 0, false,
    school.id,
    '$2y$10$x/5YKiXBT12xr7Hn7qwwkevrIegDAQzqA7wHfHwajyhD3yhmiHw1.',
    1, 0, accounts.username,
    'demo_seed', CURRENT_TIMESTAMP
FROM school
CROSS JOIN accounts
ON CONFLICT (username) DO UPDATE
SET display_name = EXCLUDED.display_name,
    employee_id = EXCLUDED.employee_id,
    account_type = EXCLUDED.account_type,
    organization_id = EXCLUDED.organization_id,
    password = EXCLUDED.password,
    status = 1,
    account_locked = false,
    must_change_password = false,
    last_update_by = 'demo_seed',
    last_update_time = CURRENT_TIMESTAMP;

-- 用已存在的角色定义授权；脚本不新增或修改 IAM 角色。
WITH role_grants(username, role_code) AS (
    VALUES
        ('demo.admin', 'SUPER_ADMIN'),
        ('demo.registrar', 'EDU_ACADEMIC_APPROVER'),
        ('demo.registrar', 'EDU_GRADE_REVIEWER'),
        ('demo.registrar', 'WORKFLOW_ADMIN')
    UNION ALL
    SELECT
        account.username, role_code
    FROM t_admin_user account
    CROSS JOIN (VALUES
        ('EDU_TEACHER'),
        ('EDU_CLASS_ADVISOR'),
        ('ROLE_PLATFORM_USER'),
        ('WORKFLOW_USER')
    ) AS roles(role_code)
    WHERE account.username LIKE 'demo.teacher.%'
    UNION ALL
    SELECT
        account.username, role_code
    FROM t_admin_user account
    CROSS JOIN (VALUES
        ('ROLE_PLATFORM_USER'),
        ('WORKFLOW_USER')
    ) AS roles(role_code)
    WHERE account.username LIKE 'demo.student.%'
       OR account.username LIKE 'demo.parent.%'
)
INSERT INTO t_user_role (user_id, role_id)
SELECT account.id, role.id
FROM role_grants grant_data
JOIN t_admin_user account
  ON account.username = grant_data.username
JOIN t_role role
  ON role.role_code = grant_data.role_code
WHERE NOT EXISTS (
    SELECT 1
    FROM t_user_role existing
    WHERE existing.user_id = account.id
      AND existing.role_id = role.id
);

INSERT INTO edu_user_profile_binding (
    id, create_by, create_time, username, profile_type, profile_id,
    status, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-binding-teacher-' || teacher.teacher_no)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP,
    CASE WHEN teacher.teacher_no LIKE 'DEMO-M%'
         THEN 'demo.teacher.major' ||
              substring(teacher.teacher_no FROM 8 FOR 2)
         ELSE 'demo.teacher.g' ||
              substring(teacher.teacher_no FROM 7 FOR 1) ||
              'c' || substring(teacher.teacher_no FROM 10 FOR 2)
    END,
    'TEACHER', teacher.id, 'ACTIVE', 'demo_seed', CURRENT_TIMESTAMP
FROM edu_teacher_profile teacher
WHERE teacher.teacher_no LIKE 'DEMO-%'
  AND NOT EXISTS (
      SELECT 1
      FROM edu_user_profile_binding existing
      WHERE existing.username = CASE WHEN teacher.teacher_no LIKE 'DEMO-M%'
          THEN 'demo.teacher.major' || substring(teacher.teacher_no FROM 8 FOR 2)
          ELSE 'demo.teacher.g' || substring(teacher.teacher_no FROM 7 FOR 1) ||
               'c' || substring(teacher.teacher_no FROM 10 FOR 2)
          END
        AND existing.profile_type = 'TEACHER'
        AND existing.profile_id = teacher.id
  );

INSERT INTO edu_user_profile_binding (
    id, create_by, create_time, username, profile_type, profile_id,
    status, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-binding-student-' || student.student_no)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP,
    'demo.student.' || lower(student.student_no),
    'STUDENT', student.id, 'ACTIVE', 'demo_seed', CURRENT_TIMESTAMP
FROM edu_student_profile student
WHERE student.student_no LIKE 'DEMO-%'
  AND NOT EXISTS (
      SELECT 1
      FROM edu_user_profile_binding existing
      WHERE existing.username = 'demo.student.' || lower(student.student_no)
        AND existing.profile_type = 'STUDENT'
        AND existing.profile_id = student.id
  );

INSERT INTO edu_user_profile_binding (
    id, create_by, create_time, username, profile_type, profile_id,
    status, last_update_by, last_update_time
)
SELECT
    md5('chronos-demo-binding-parent-' || parent.parent_no)::uuid::text,
    'demo_seed', CURRENT_TIMESTAMP,
    'demo.parent.' || lower(replace(parent.parent_no, 'DEMO-PARENT-', '')),
    'PARENT', parent.id, 'ACTIVE', 'demo_seed', CURRENT_TIMESTAMP
FROM edu_parent_profile parent
WHERE parent.parent_no LIKE 'DEMO-PARENT-%'
  AND NOT EXISTS (
      SELECT 1
      FROM edu_user_profile_binding existing
      WHERE existing.username =
          'demo.parent.' ||
          lower(replace(parent.parent_no, 'DEMO-PARENT-', ''))
        AND existing.profile_type = 'PARENT'
        AND existing.profile_id = parent.id
  );

-- 教务身份绑定也是家校中心唯一的账号绑定来源。
UPDATE edu_user_profile_binding
SET verified_at = COALESCE(verified_at, CURRENT_TIMESTAMP)
WHERE profile_type = 'PARENT'
  AND status = 'ACTIVE'
  AND username LIKE 'demo.parent.%';

-- ---------------------------------------------------------------------------
-- 8. 数量与关键关系断言
-- ---------------------------------------------------------------------------

DO $$
DECLARE
    actual_count bigint;
BEGIN
    SELECT count(*) INTO actual_count
    FROM t_organization
    WHERE org_code = 'DEMO-SCHOOL-001';
    IF actual_count <> 1 THEN
        RAISE EXCEPTION 'demo school count expected 1, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM t_organization_unit
    WHERE organization_unit_code LIKE 'DEMO-%';
    IF actual_count <> 17 THEN
        RAISE EXCEPTION 'demo organization unit count expected 17, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_grade
    WHERE grade_code LIKE 'DEMO-GRADE-%';
    IF actual_count <> 4 THEN
        RAISE EXCEPTION 'demo grade count expected 4, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_major
    WHERE major_code LIKE 'DEMO-MAJOR-%';
    IF actual_count <> 10 THEN
        RAISE EXCEPTION 'demo major count expected 10, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_administrative_class
    WHERE class_code LIKE 'DEMO-G%-C%';
    IF actual_count <> 40 THEN
        RAISE EXCEPTION 'demo class count expected 40, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_student_profile
    WHERE student_no LIKE 'DEMO-%';
    IF actual_count <> 2000 THEN
        RAISE EXCEPTION 'demo student count expected 2000, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_teacher_profile
    WHERE teacher_no LIKE 'DEMO-%';
    IF actual_count <> 50 THEN
        RAISE EXCEPTION 'demo teacher count expected 50, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_classroom
    WHERE room_code LIKE 'DEMO-%';
    IF actual_count <> 50 THEN
        RAISE EXCEPTION 'demo classroom count expected 50, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_parent_profile
    WHERE parent_no LIKE 'DEMO-PARENT-%';
    IF actual_count <> 2000 THEN
        RAISE EXCEPTION 'demo parent count expected 2000, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_student_guardian guardian
    JOIN edu_student_profile student ON student.id = guardian.student_id
    WHERE student.student_no LIKE 'DEMO-%';
    IF actual_count <> 2000 THEN
        RAISE EXCEPTION 'demo guardian count expected 2000, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM t_admin_user
    WHERE username LIKE 'demo.%';
    IF actual_count <> 4052 THEN
        RAISE EXCEPTION 'demo account count expected 4052, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_user_profile_binding
    WHERE username LIKE 'demo.%';
    IF actual_count <> 4050 THEN
        RAISE EXCEPTION 'demo profile binding count expected 4050, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM edu_student_profile student
    JOIN edu_administrative_class class_data
      ON class_data.id = student.administrative_class_id
    WHERE student.student_no LIKE 'DEMO-%'
    GROUP BY class_data.id
    HAVING count(*) <> 50;
    IF actual_count <> 0 THEN
        RAISE EXCEPTION 'every demo class must contain exactly 50 students';
    END IF;
END
$$;

COMMIT;
