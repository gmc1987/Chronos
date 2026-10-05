-- 本地生产演示规模数据。依赖已有 2026-2027-1 学期；可重复执行。
-- 用法：docker cp 本文件 Chronos:/tmp/education-scale-acceptance-data.sql
--       docker exec Chronos psql -v ON_ERROR_STOP=1 -U Chronos -d ChronosEducation -f /tmp/education-scale-acceptance-data.sql
-- 首次执行后从容器复制 /tmp/chronos-scale-credentials.csv，并以 0600 权限保存。

CREATE TEMP TABLE scale_demo_credentials (username text PRIMARY KEY, password text NOT NULL);
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('chronos-education-scale-acceptance-2026'));

WITH schools(code, name, short_name) AS (
    VALUES ('SCALE-HV-2026', '星河高等职业技术学院（演示）', '星河高职'),
           ('SCALE-JC-2026', '星河专科学院（演示）', '星河大专'),
           ('SCALE-UG-2026', '星河应用技术大学（演示）', '星河本科')
)
INSERT INTO t_organization (id, create_by, create_time, org_code,
    organization_name, organization_type, register_time, short_name, status,
    timezone, industries)
SELECT md5('scale-org-' || code)::uuid::text, 'scale_seed', now(), code,
       name, 'SCHOOL', now(), short_name, 1, 'Asia/Shanghai', 'EDUCATION'
FROM schools ON CONFLICT (org_code) DO NOTHING;

WITH grades(school, year, stage) AS (
    SELECT 'HV', year, 'HIGHER_EDUCATION' FROM generate_series(2024, 2026) year
    UNION ALL SELECT 'JC', year, 'HIGHER_EDUCATION' FROM generate_series(2025, 2026) year
    UNION ALL SELECT 'UG', year, 'HIGHER_EDUCATION' FROM generate_series(2023, 2026) year
)
INSERT INTO edu_grade (id, grade_code, grade_name, enrollment_year,
    education_stage, enabled, sort_order, create_by, create_time)
SELECT md5('scale-grade-' || school || year)::uuid::text,
       'SCALE-' || school || '-' || year,
       year || '级' || CASE school WHEN 'HV' THEN '高职' WHEN 'JC' THEN '大专' ELSE '本科' END,
       year, stage, true, year, 'scale_seed', now()
FROM grades ON CONFLICT (grade_code) DO NOTHING;

WITH majors(no, name) AS (
    VALUES (1, '计算机应用技术'), (2, '软件技术'), (3, '电子商务'),
           (4, '数字媒体技术'), (5, '机电一体化技术'), (6, '工业机器人技术'),
           (7, '汽车检测与维修技术'), (8, '建筑工程技术'), (9, '大数据与会计'),
           (10, '学前教育'), (11, '护理'), (12, '旅游管理'),
           (13, '智能制造装备技术'), (14, '大数据技术')
)
INSERT INTO edu_major (id, major_code, major_name, schooling_years,
    enabled, create_by, create_time)
SELECT md5('scale-major-' || no)::uuid::text,
       'SCALE-M' || lpad(no::text, 2, '0'), name, 3, true, 'scale_seed', now()
FROM majors ON CONFLICT (major_code) DO NOTHING;

-- 68 名高职教师，另为大专、本科小样本各配置 4 名教师。
WITH teachers AS (
    SELECT 'HV' AS school, n, '高职演示教师' || lpad(n::text, 2, '0') AS name
    FROM generate_series(1, 68) n
    UNION ALL SELECT 'JC', n, '大专演示教师' || lpad(n::text, 2, '0')
    FROM generate_series(1, 4) n
    UNION ALL SELECT 'UG', n, '本科演示教师' || lpad(n::text, 2, '0')
    FROM generate_series(1, 4) n
)
INSERT INTO t_iam_employee (id, employee_code, employee_name, employee_type,
    employment_status, gender, hire_date, create_by, create_time)
SELECT md5('scale-employee-' || school || n)::uuid::text,
       'SCALE-' || school || '-T' || lpad(n::text, 3, '0'), name,
       'TEACHER', 'ACTIVE', CASE WHEN n % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END,
       DATE '2023-09-01', 'scale_seed', now()
FROM teachers ON CONFLICT (employee_code) DO NOTHING;

WITH teachers AS (
    SELECT 'HV' AS school, n FROM generate_series(1, 68) n
    UNION ALL SELECT 'JC', n FROM generate_series(1, 4) n
    UNION ALL SELECT 'UG', n FROM generate_series(1, 4) n
)
INSERT INTO edu_teacher_profile (id, teacher_no, teacher_name, employee_id,
    specialty, enabled, employment_status, max_weekly_lessons,
    max_daily_lessons, max_consecutive_lessons, create_by, create_time)
SELECT md5('scale-teacher-' || t.school || t.n)::uuid::text,
       'SCALE-' || t.school || '-T' || lpad(t.n::text, 3, '0'),
       e.employee_name, e.id,
       CASE WHEN t.n <= 14 THEN '公共基础课程' ELSE '专业课程与实践教学' END,
       true, 'ACTIVE', 20, 6, 4, 'scale_seed', now()
FROM teachers t JOIN t_iam_employee e ON e.employee_code =
    'SCALE-' || t.school || '-T' || lpad(t.n::text, 3, '0')
ON CONFLICT (teacher_no) DO NOTHING;

-- 高职为 3 年级 × 14 专业 × 每专业每年级 4 班；其他两校各 4 班。
CREATE TEMP TABLE scale_demo_classes AS
SELECT 'HV'::text AS school, g AS year, m AS major_no, c AS class_no,
       'SCALE-HV-' || g || '-M' || lpad(m::text, 2, '0') || '-C' || c AS class_code,
       45 + ((g + m + c) % 6) AS class_size,
       row_number() OVER (ORDER BY g, m, c)::integer AS ordinal
FROM generate_series(2024, 2026) g
CROSS JOIN generate_series(1, 14) m
CROSS JOIN generate_series(1, 4) c
UNION ALL
SELECT school, year, major_no, class_no,
       'SCALE-' || school || '-' || year || '-M' || lpad(major_no::text, 2, '0') || '-C' || class_no,
       45 + ((year + major_no + class_no) % 6),
       168 + row_number() OVER (ORDER BY school, year, major_no, class_no)::integer
FROM (VALUES ('JC', 2025, 1, 1), ('JC', 2026, 2, 1),
             ('JC', 2026, 3, 1), ('JC', 2025, 4, 1),
             ('UG', 2023, 5, 1), ('UG', 2024, 6, 1),
             ('UG', 2025, 7, 1), ('UG', 2026, 8, 1)) samples(school, year, major_no, class_no);

INSERT INTO edu_administrative_class (id, class_code, class_name, campus_id,
    grade_year, grade_id, major_id, head_teacher_id, status, create_by, create_time)
SELECT md5('scale-class-' || c.class_code)::uuid::text, c.class_code,
       c.year || '级' || m.major_name || c.class_no || '班', o.id, c.year,
       g.id, m.id, t.id, 'ACTIVE', 'scale_seed', now()
FROM scale_demo_classes c
JOIN t_organization o ON o.org_code = 'SCALE-' || c.school || '-2026'
JOIN edu_grade g ON g.grade_code = 'SCALE-' || c.school || '-' || c.year
JOIN edu_major m ON m.major_code = 'SCALE-M' || lpad(c.major_no::text, 2, '0')
JOIN edu_teacher_profile t ON t.teacher_no = 'SCALE-' || c.school || '-T' ||
    lpad((CASE WHEN c.school = 'HV' THEN ((c.ordinal - 1) % 68) + 1
               ELSE ((c.ordinal - 169) % 4) + 1 END)::text, 3, '0')
ON CONFLICT (class_code) DO NOTHING;

INSERT INTO edu_classroom (id, room_code, room_name, campus_id, building_name,
    capacity, room_type, enabled, create_by, create_time)
SELECT md5('scale-room-' || c.class_code)::uuid::text,
       'ROOM-' || c.class_code, c.year || '级' || c.major_no || '专业' || c.class_no || '班教室',
       o.id, '综合教学楼', 52, 'STANDARD', true, 'scale_seed', now()
FROM scale_demo_classes c
JOIN t_organization o ON o.org_code = 'SCALE-' || c.school || '-2026'
ON CONFLICT (room_code) DO NOTHING;

-- 各校区八节标准作息，保证考试占课、日期课表与周课表共用节次时间。
INSERT INTO edu_bell_schedule (id, academic_term_id, campus_id,
    schedule_code, schedule_name, default_schedule, status, create_by, create_time)
SELECT md5('scale-bell-' || o.org_code)::uuid::text, term.id, o.id,
       'BELL-' || o.org_code, o.short_name || '标准作息', true, 'ACTIVE',
       'scale_seed', now()
FROM t_organization o CROSS JOIN edu_academic_term term
WHERE o.org_code LIKE 'SCALE-%-2026' AND term.term_code = '2026-2027-1'
  AND NOT EXISTS (SELECT 1 FROM edu_bell_schedule b
                  WHERE b.schedule_code = 'BELL-' || o.org_code);

WITH periods(no, segment, starts, ends) AS (
    VALUES (1, 'MORNING', TIME '08:00', TIME '08:45'),
           (2, 'MORNING', TIME '08:55', TIME '09:40'),
           (3, 'MORNING', TIME '10:00', TIME '10:45'),
           (4, 'MORNING', TIME '10:55', TIME '11:40'),
           (5, 'AFTERNOON', TIME '14:00', TIME '14:45'),
           (6, 'AFTERNOON', TIME '14:55', TIME '15:40'),
           (7, 'AFTERNOON', TIME '16:00', TIME '16:45'),
           (8, 'AFTERNOON', TIME '16:55', TIME '17:40')
)
INSERT INTO edu_bell_period (id, bell_schedule_id, period_no,
    period_name, day_segment, start_time, end_time, schedulable,
    create_by, create_time)
SELECT md5('scale-bell-period-' || b.id || '-' || p.no)::uuid::text,
       b.id, p.no, '第' || p.no || '节', p.segment, p.starts, p.ends,
       true, 'scale_seed', now()
FROM edu_bell_schedule b CROSS JOIN periods p
WHERE b.schedule_code LIKE 'BELL-SCALE-%-2026'
  AND NOT EXISTS (SELECT 1 FROM edu_bell_period existing
                  WHERE existing.bell_schedule_id = b.id AND existing.period_no = p.no);

WITH students AS (
    SELECT c.*, n AS student_index,
           c.class_code || '-S' || lpad(n::text, 2, '0') AS student_no
    FROM scale_demo_classes c
    CROSS JOIN LATERAL generate_series(1, c.class_size) n
)
INSERT INTO edu_student_profile (id, student_no, student_name,
    administrative_class_id, enrollment_status, gender, grade_year,
    grade_id, major_id, create_by, create_time)
SELECT md5('scale-student-' || s.student_no)::uuid::text, s.student_no,
       '演示学生' || lpad(s.ordinal::text, 3, '0') || '-' || lpad(s.student_index::text, 2, '0'),
       c.id, 'ACTIVE', CASE WHEN s.student_index % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END,
       s.year, g.id, m.id, 'scale_seed', now()
FROM students s
JOIN edu_administrative_class c ON c.class_code = s.class_code
JOIN edu_grade g ON g.grade_code = 'SCALE-' || s.school || '-' || s.year
JOIN edu_major m ON m.major_code = 'SCALE-M' || lpad(s.major_no::text, 2, '0')
ON CONFLICT (student_no) DO NOTHING;

-- 题库与排课使用的课程维度：14 门专业核心课程和公共数学、英语。
WITH subjects(code, name, category, ord) AS (
    SELECT 'SCALE-S' || lpad(m::text, 2, '0'),
           major_name || '专业基础', 'PROFESSIONAL', m
    FROM (VALUES (1, '计算机应用技术'), (2, '软件技术'), (3, '电子商务'),
                 (4, '数字媒体技术'), (5, '机电一体化技术'), (6, '工业机器人技术'),
                 (7, '汽车检测与维修技术'), (8, '建筑工程技术'), (9, '大数据与会计'),
                 (10, '学前教育'), (11, '护理'), (12, '旅游管理'),
                 (13, '智能制造装备技术'), (14, '大数据技术')) v(m, major_name)
    UNION ALL VALUES ('SCALE-MATH', '高等数学', 'GENERAL', 15),
                     ('SCALE-ENG', '大学英语', 'GENERAL', 16)
)
INSERT INTO edu_subject (id, subject_code, subject_name, subject_category,
    enabled, sort_order, create_by, create_time)
SELECT md5('scale-subject-' || code)::uuid::text, code, name, category,
       true, ord, 'scale_seed', now()
FROM subjects ON CONFLICT (subject_code) DO NOTHING;

WITH courses AS (
    SELECT 'SCALE-C' || lpad(m::text, 2, '0') AS code,
           major_name || '专业基础' AS name,
           'SCALE-S' || lpad(m::text, 2, '0') AS subject_code,
           'PROFESSIONAL' AS category, 80 AS hours
    FROM (VALUES (1, '计算机应用技术'), (2, '软件技术'), (3, '电子商务'),
                 (4, '数字媒体技术'), (5, '机电一体化技术'), (6, '工业机器人技术'),
                 (7, '汽车检测与维修技术'), (8, '建筑工程技术'), (9, '大数据与会计'),
                 (10, '学前教育'), (11, '护理'), (12, '旅游管理'),
                 (13, '智能制造装备技术'), (14, '大数据技术')) v(m, major_name)
    UNION ALL VALUES ('SCALE-CMATH', '高等数学', 'SCALE-MATH', 'GENERAL', 40),
                     ('SCALE-CENG', '大学英语', 'SCALE-ENG', 'GENERAL', 40)
)
INSERT INTO edu_course_catalog (id, course_code, course_name, course_category,
    course_nature, total_hours, theory_hours, practice_hours, enabled,
    subject_id, create_by, create_time)
SELECT md5('scale-course-' || code)::uuid::text, code, name, category,
       'REQUIRED', hours, hours, 0, true, s.id, 'scale_seed', now()
FROM courses c JOIN edu_subject s ON s.subject_code = c.subject_code
ON CONFLICT (course_code) DO NOTHING;

-- 每班开设 1 门专业基础课和 2 门公共课，保留后续自动排课的真实任务池。
WITH offering_rows AS (
    SELECT c.*, kind,
           CASE kind WHEN 'MAJOR' THEN 'SCALE-C' || lpad(c.major_no::text, 2, '0')
                     WHEN 'MATH' THEN 'SCALE-CMATH' ELSE 'SCALE-CENG' END AS course_code,
           CASE kind WHEN 'MAJOR' THEN 2 ELSE 1 END AS lessons,
           CASE WHEN c.school = 'HV'
                THEN ((c.ordinal * 3 + CASE kind WHEN 'MAJOR' THEN 0 WHEN 'MATH' THEN 1 ELSE 2 END) % 68) + 1
                ELSE ((c.ordinal + CASE kind WHEN 'MAJOR' THEN 0 WHEN 'MATH' THEN 1 ELSE 2 END) % 4) + 1
           END AS teacher_no
    FROM scale_demo_classes c
    CROSS JOIN (VALUES ('MAJOR'), ('MATH'), ('ENG')) k(kind)
)
INSERT INTO edu_course_offering (id, semester_code, offering_code, course_code,
    course_name, teaching_class_name, teacher_id, teacher_name, student_count,
    weekly_lessons, campus_id, status, preferred_duration_periods, week_pattern,
    offering_mode, create_by, create_time)
SELECT md5('scale-offering-' || r.class_code || '-' || r.kind)::uuid::text,
       '2026-2027-1', r.class_code || '-' || r.kind, r.course_code,
       cc.course_name, ac.class_name || '·' || cc.course_name, t.id, t.teacher_name,
       r.class_size, r.lessons, o.id, 'ACTIVE', 1, 'ALL', 'NORMAL', 'scale_seed', now()
FROM offering_rows r
JOIN edu_course_catalog cc ON cc.course_code = r.course_code
JOIN edu_administrative_class ac ON ac.class_code = r.class_code
JOIN t_organization o ON o.org_code = 'SCALE-' || r.school || '-2026'
JOIN edu_teacher_profile t ON t.teacher_no = 'SCALE-' || r.school || '-T' || lpad(r.teacher_no::text, 3, '0')
ON CONFLICT (semester_code, offering_code) DO NOTHING;

INSERT INTO edu_teaching_class_member (id, offering_id, student_id,
    enrollment_status, enrollment_source, enrolled_at, create_by, create_time)
SELECT md5('scale-member-' || o.id || '-' || s.id)::uuid::text,
       o.id, s.id, 'ENROLLED', 'SOURCE_CLASS', now(), 'scale_seed', now()
FROM edu_course_offering o
JOIN scale_demo_classes c ON o.offering_code LIKE c.class_code || '-%'
JOIN edu_administrative_class ac ON ac.class_code = c.class_code
JOIN edu_student_profile s ON s.administrative_class_id = ac.id
WHERE o.offering_code LIKE 'SCALE-%'
ON CONFLICT (offering_id, student_id) DO NOTHING;

-- 为每位教师和学生开通一对一账号；仅首次创建时生成随机密码。
INSERT INTO scale_demo_credentials (username, password)
SELECT lower(t.teacher_no), encode(gen_random_bytes(12), 'hex')
FROM edu_teacher_profile t WHERE t.teacher_no LIKE 'SCALE-%'
  AND NOT EXISTS (SELECT 1 FROM t_admin_user u WHERE u.username = lower(t.teacher_no))
UNION ALL
SELECT lower(s.student_no), encode(gen_random_bytes(12), 'hex')
FROM edu_student_profile s WHERE s.student_no LIKE 'SCALE-%'
  AND NOT EXISTS (SELECT 1 FROM t_admin_user u WHERE u.username = lower(s.student_no));

INSERT INTO t_admin_user (id, create_by, create_time, account_locked,
    account_type, display_name, employee_id, failed_login_attempts,
    must_change_password, organization_id, password, status, token_version,
    username)
SELECT md5('scale-account-' || c.username)::uuid::text, 'scale_seed', now(),
       false, CASE WHEN t.id IS NOT NULL THEN 'STAFF' ELSE 'STUDENT' END,
       COALESCE(t.teacher_name, s.student_name), t.employee_id, 0, false,
       o.id, crypt(c.password, gen_salt('bf', 4)), 1, 0, c.username
FROM scale_demo_credentials c
LEFT JOIN edu_teacher_profile t ON lower(t.teacher_no) = c.username
LEFT JOIN edu_student_profile s ON lower(s.student_no) = c.username
JOIN t_organization o ON o.org_code = 'SCALE-' ||
    upper(split_part(c.username, '-', 2)) || '-2026'
ON CONFLICT (username) DO NOTHING;

INSERT INTO edu_user_profile_binding (id, username, profile_type, profile_id,
    status, create_by, create_time)
SELECT md5('scale-binding-teacher-' || t.teacher_no)::uuid::text,
       lower(t.teacher_no), 'TEACHER', t.id, 'ACTIVE', 'scale_seed', now()
FROM edu_teacher_profile t WHERE t.teacher_no LIKE 'SCALE-%'
  AND NOT EXISTS (SELECT 1 FROM edu_user_profile_binding b WHERE b.username = lower(t.teacher_no))
UNION ALL
SELECT md5('scale-binding-student-' || s.student_no)::uuid::text,
       lower(s.student_no), 'STUDENT', s.id, 'ACTIVE', 'scale_seed', now()
FROM edu_student_profile s WHERE s.student_no LIKE 'SCALE-%'
  AND NOT EXISTS (SELECT 1 FROM edu_user_profile_binding b WHERE b.username = lower(s.student_no));

INSERT INTO t_user_role (user_id, role_id)
SELECT u.id, r.id
FROM t_admin_user u
JOIN t_role r ON r.role_code = CASE WHEN u.account_type = 'STAFF'
                                   THEN 'EDU_TEACHER' ELSE 'ROLE_PLATFORM_USER' END
WHERE u.username LIKE 'scale-%'
  AND NOT EXISTS (SELECT 1 FROM t_user_role existing
                  WHERE existing.user_id = u.id AND existing.role_id = r.id);

-- 两名不同的演示教师承担成绩册两级审核，避免录入人审核自己的成绩册。
INSERT INTO t_user_role (user_id, role_id)
SELECT u.id, r.id
FROM t_admin_user u
JOIN t_role r ON r.role_code = CASE u.username
  WHEN 'scale-hv-t005' THEN 'EDU_GRADE_REVIEWER'
  WHEN 'scale-hv-t006' THEN 'EDU_ACADEMIC_APPROVER'
END
WHERE u.username IN ('scale-hv-t005', 'scale-hv-t006')
ON CONFLICT DO NOTHING;

-- 审核只能读取演示学校的成绩册；角色本身不授予全平台数据范围。
INSERT INTO t_role_data_scope (id, create_by, create_time, role_id, scope_type, organization_id)
SELECT gen_random_uuid(), 'scale-demo', now(), r.id, 'ORGANIZATION', u.organization_id
FROM t_admin_user u
JOIN t_role r ON r.role_code = CASE u.username
  WHEN 'scale-hv-t005' THEN 'EDU_GRADE_REVIEWER'
  WHEN 'scale-hv-t006' THEN 'EDU_ACADEMIC_APPROVER'
END
WHERE u.username IN ('scale-hv-t005', 'scale-hv-t006')
  AND u.organization_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM t_role_data_scope existing
    WHERE existing.role_id = r.id
      AND existing.scope_type = 'ORGANIZATION'
      AND existing.organization_id = u.organization_id
  );

-- The demo teacher role may have been provisioned after the platform migrations.
-- Keep its teaching and homework grants reproducible when this seed is rerun.
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code = 'EDU_TEACHER'
  AND (permission.permission_code = 'education:teaching:view'
       OR permission.permission_code LIKE 'education:homework:%')
ON CONFLICT DO NOTHING;

DO $$ BEGIN
    IF (SELECT count(*) FROM edu_administrative_class WHERE class_code LIKE 'SCALE-HV-%') <> 168 THEN
        RAISE EXCEPTION '高职班级数量不是 168';
    END IF;
    IF (SELECT count(*) FROM edu_teacher_profile WHERE teacher_no LIKE 'SCALE-HV-%') <> 68 THEN
        RAISE EXCEPTION '高职教师数量不是 68';
    END IF;
    IF EXISTS (SELECT 1 FROM edu_administrative_class c
               JOIN edu_student_profile s ON s.administrative_class_id = c.id
               WHERE c.class_code LIKE 'SCALE-HV-%'
               GROUP BY c.id HAVING count(*) NOT BETWEEN 45 AND 50) THEN
        RAISE EXCEPTION '高职班级人数超出 45-50';
    END IF;
END $$;
COMMIT;

\copy scale_demo_credentials TO '/tmp/chronos-scale-credentials.csv' CSV HEADER
