-- 智慧校园 MVP 基础领域测试数据，需先执行 education-campus-mvp-foundation-v1.sql。
-- 复用现有中职测试数据并补齐年级、学科、家长和教师任教关系。

BEGIN;
SELECT pg_advisory_xact_lock(hashtext('education-campus-mvp-demo-data-v1'));

-- 早期走班样例把 teaching task 的 teacher_id 写成了员工 ID。
-- 当前领域模型统一使用教师档案 ID，按教师姓名只修复这批明确的 PoC 样例数据。
UPDATE edu_course_offering offering
SET teacher_id = teacher.id,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM edu_teacher_profile teacher
WHERE offering.teacher_name = teacher.teacher_name
  AND offering.semester_code = '2026-2027-1'
  AND offering.offering_code IN (
      'ZB-JD-001', 'ZB-JD-002', 'ZB-QX-001', 'ZB-SK-001',
      'ZB-JS-001', 'ZB-JS-002', 'ZB-DS-001', 'ZB-SM-001',
      'GG-YW-001', 'GG-SX-001', 'GG-YY-001', 'GG-ZY-001'
  );

INSERT INTO edu_grade (
    id, grade_code, grade_name, enrollment_year, education_stage,
    enabled, sort_order, create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, 'GRADE-2026', '2026级', 2026, 'VOCATIONAL',
    true, 10, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM edu_grade WHERE grade_code = 'GRADE-2026'
);

UPDATE edu_administrative_class class_data
SET grade_id = grade_data.id,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM edu_grade grade_data
WHERE grade_data.grade_code = 'GRADE-2026'
  AND class_data.grade_year = 2026;

UPDATE edu_student_profile student
SET grade_id = grade_data.id,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM edu_grade grade_data
WHERE grade_data.grade_code = 'GRADE-2026'
  AND student.grade_year = 2026;

CREATE TEMP TABLE tmp_subject (
    subject_code varchar(64) NOT NULL,
    subject_name varchar(128) NOT NULL,
    subject_category varchar(32) NOT NULL,
    course_code varchar(64) NOT NULL,
    sort_order integer NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_subject VALUES
    ('SUB-CHINESE', '语文', 'GENERAL', 'GG101', 10),
    ('SUB-MATH', '数学', 'GENERAL', 'GG102', 20),
    ('SUB-ENGLISH', '英语', 'GENERAL', 'GG103', 30),
    ('SUB-MORAL', '思想政治', 'GENERAL', 'GG104', 40),
    ('SUB-IT', '信息技术', 'GENERAL', 'GG105', 50),
    ('SUB-ECOMMERCE', '电子商务', 'PROFESSIONAL', 'DS101', 110),
    ('SUB-LIVE-COMMERCE', '直播电商', 'PROFESSIONAL', 'DS102', 120),
    ('SUB-ELECTRICAL', '电工电子', 'PROFESSIONAL', 'JD101', 130),
    ('SUB-PLC', 'PLC控制技术', 'PROFESSIONAL', 'JD201', 140),
    ('SUB-PYTHON', '程序设计', 'PROFESSIONAL', 'JS101', 150),
    ('SUB-DATABASE', '数据库技术', 'PROFESSIONAL', 'JS102', 160),
    ('SUB-NETWORK', '计算机网络', 'PROFESSIONAL', 'JS202', 170),
    ('SUB-AUTO', '汽车维修', 'PROFESSIONAL', 'QX101', 180),
    ('SUB-CNC', '数控加工', 'PROFESSIONAL', 'SK201', 190),
    ('SUB-VIDEO', '数字视频', 'PROFESSIONAL', 'SM101', 200),
    ('SUB-GRAPHICS', '图形图像', 'PROFESSIONAL', 'SM102', 210);

INSERT INTO edu_subject (
    id, subject_code, subject_name, subject_category, enabled, sort_order,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, source.subject_code, source.subject_name,
    source.subject_category, true, source.sort_order,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_subject source
WHERE NOT EXISTS (
    SELECT 1 FROM edu_subject existing
    WHERE existing.subject_code = source.subject_code
);

UPDATE edu_course_catalog course
SET subject_id = subject.id,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM tmp_subject mapping
JOIN edu_subject subject ON subject.subject_code = mapping.subject_code
WHERE course.course_code = mapping.course_code;

-- 每名测试学生创建一名主监护人，手机号为测试号码，不代表真实个人信息。
INSERT INTO edu_parent_profile (
    id, parent_no, parent_name, gender, phone, employment, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    'P-' || student.student_no,
    student.student_name || '家长',
    CASE WHEN row_number() OVER (ORDER BY student.student_no) % 2 = 0
         THEN 'FEMALE' ELSE 'MALE' END,
    '199' || lpad(row_number() OVER (ORDER BY student.student_no)::text, 8, '0'),
    '测试监护人',
    'ACTIVE',
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM edu_student_profile student
WHERE NOT EXISTS (
    SELECT 1 FROM edu_parent_profile existing
    WHERE existing.parent_no = 'P-' || student.student_no
);

INSERT INTO edu_student_guardian (
    id, student_id, parent_id, relationship,
    primary_guardian, emergency_contact,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text, student.id, parent.id,
    CASE WHEN parent.gender = 'FEMALE' THEN 'MOTHER' ELSE 'FATHER' END,
    true, true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM edu_student_profile student
JOIN edu_parent_profile parent ON parent.parent_no = 'P-' || student.student_no
WHERE NOT EXISTS (
    SELECT 1 FROM edu_student_guardian existing
    WHERE existing.student_id = student.id
      AND existing.parent_id = parent.id
);

-- 为首个家长补充第二名学生关系，用于验证家长门户多子女切换。
-- 原学生仍是主监护关系，第二名学生仅作为普通监护关系，不改变其原主监护人。
WITH first_parent AS (
    SELECT id
    FROM edu_parent_profile
    WHERE status = 'ACTIVE'
    ORDER BY parent_no
    LIMIT 1
), related_students AS (
    SELECT guardian.student_id
    FROM edu_student_guardian guardian
    JOIN first_parent parent ON parent.id = guardian.parent_id
), second_student AS (
    SELECT student.id
    FROM edu_student_profile student
    WHERE student.enrollment_status = 'ACTIVE'
      AND NOT EXISTS (
          SELECT 1
          FROM related_students related
          WHERE related.student_id = student.id
      )
    ORDER BY student.student_no
    LIMIT 1
)
INSERT INTO edu_student_guardian (
    id, student_id, parent_id, relationship,
    primary_guardian, emergency_contact,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    student.id,
    parent.id,
    'GUARDIAN',
    false,
    false,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
FROM first_parent parent
CROSS JOIN second_student student
ON CONFLICT (student_id, parent_id) DO NOTHING;

CREATE TEMP TABLE tmp_teacher_assignment (
    teacher_no varchar(64) NOT NULL,
    subject_code varchar(64) NOT NULL,
    class_prefix varchar(16),
    weekly_lessons integer NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_teacher_assignment VALUES
    ('T2026101', 'SUB-PLC', '2026-JD', 8),
    ('T2026102', 'SUB-DATABASE', '2026-JS', 8),
    ('T2026103', 'SUB-CNC', '2026-SK', 8),
    ('T2026104', 'SUB-NETWORK', '2026-JS', 8),
    ('T2026105', 'SUB-AUTO', '2026-QX', 8),
    ('T2026106', 'SUB-ECOMMERCE', '2026-DS', 8),
    ('T2026107', 'SUB-ELECTRICAL', '2026-JD', 6),
    ('T2026108', 'SUB-VIDEO', '2026-SM', 8),
    ('T2026109', 'SUB-CHINESE', NULL, 12),
    ('T2026110', 'SUB-MATH', NULL, 12),
    ('T2026111', 'SUB-ENGLISH', NULL, 12),
    ('T2026112', 'SUB-MORAL', NULL, 10);

INSERT INTO edu_teacher_teaching_assignment (
    id, academic_term_id, teacher_id, subject_id, grade_id,
    administrative_class_id, assignment_role, weekly_lessons, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    term.id,
    teacher.id,
    subject.id,
    grade.id,
    class_data.id,
    'TEACHER',
    mapping.weekly_lessons,
    true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_teacher_assignment mapping
JOIN edu_teacher_profile teacher ON teacher.teacher_no = mapping.teacher_no
JOIN edu_subject subject ON subject.subject_code = mapping.subject_code
JOIN edu_grade grade ON grade.grade_code = 'GRADE-2026'
JOIN edu_academic_term term ON term.current_term = true
LEFT JOIN edu_administrative_class class_data
  ON mapping.class_prefix IS NOT NULL
 AND class_data.class_code LIKE mapping.class_prefix || '%'
WHERE NOT EXISTS (
    SELECT 1 FROM edu_teacher_teaching_assignment existing
    WHERE existing.academic_term_id = term.id
      AND existing.teacher_id = teacher.id
      AND existing.subject_id = subject.id
      AND existing.administrative_class_id IS NOT DISTINCT FROM class_data.id
);

COMMIT;
