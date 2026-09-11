-- 中专/技校教育基础测试数据 V1。
-- 目标数据库：ChronosEducation。
-- 覆盖：学校/校区、组织架构、教师、学期、专业、行政班、课程、学生。
-- 主键由 PostgreSQL 生成 UUID，业务关联通过稳定业务编码解析，脚本可重复执行。

BEGIN;

SELECT pg_advisory_xact_lock(hashtext('education-vocational-master-data-v1'));

-- 学校及校区。
INSERT INTO t_organization (
    id, organization_name, org_code, organization_type, short_name,
    timezone, status, sort_order, parent_org_id, description,
    mailing_address, tel, industries, register_time,
    create_by, create_time, last_update_by, last_update_time
)
VALUES (
    gen_random_uuid()::text, '南城现代职业技术学校', 'EDU-NCVC', 'SCHOOL', '南城职校',
    'Asia/Shanghai', 1, 10, NULL, '中等职业教育综合测试学校',
    '南城市科教路 88 号', '020-88001000', 'EDUCATION', CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
)
ON CONFLICT (org_code) DO UPDATE SET
    organization_name = EXCLUDED.organization_name,
    organization_type = EXCLUDED.organization_type,
    short_name = EXCLUDED.short_name,
    status = 1,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO t_organization (
    id, organization_name, org_code, organization_type, short_name,
    timezone, status, sort_order, parent_org_id, description,
    mailing_address, tel, industries, register_time,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    campus.organization_name,
    campus.org_code,
    'CAMPUS',
    campus.short_name,
    'Asia/Shanghai',
    1,
    campus.sort_order,
    school.id,
    campus.description,
    campus.address,
    campus.tel,
    'EDUCATION',
    CURRENT_TIMESTAMP,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('南城现代职业技术学校主校区', 'EDU-NCVC-MAIN', '主校区', 11, '文化基础课与信息商贸专业校区', '南城市科教路 88 号', '020-88001001'),
    ('南城现代职业技术学校实训校区', 'EDU-NCVC-TRAIN', '实训校区', 12, '智能制造与交通运输专业实训校区', '南城市工匠大道 16 号', '020-88001002')
) AS campus(organization_name, org_code, short_name, sort_order, description, address, tel)
JOIN t_organization school ON school.org_code = 'EDU-NCVC'
ON CONFLICT (org_code) DO UPDATE SET
    organization_name = EXCLUDED.organization_name,
    organization_type = EXCLUDED.organization_type,
    short_name = EXCLUDED.short_name,
    parent_org_id = EXCLUDED.parent_org_id,
    status = 1,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 学校内部组织架构。业务编码用于幂等识别，不依赖固定主键。
INSERT INTO t_organization_unit (
    id, organization_unit_name, organization_unit_code, unit_type,
    org_id, parent_organization_unit_id, level, tree_path,
    sort_order, status, description,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    definition.unit_name,
    definition.unit_code,
    definition.unit_type,
    school.id,
    NULL,
    1,
    '/' || definition.unit_code,
    definition.sort_order,
    1,
    definition.description,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('校长办公室', 'NCVC-OFFICE', 'ADMINISTRATIVE', 10, '学校综合行政管理'),
    ('教务处', 'NCVC-ACADEMIC-AFFAIRS', 'ADMINISTRATIVE', 20, '教学运行与学籍管理'),
    ('学生工作处', 'NCVC-STUDENT-AFFAIRS', 'ADMINISTRATIVE', 30, '学生管理与德育工作'),
    ('信息技术系', 'NCVC-IT', 'ACADEMIC', 40, '计算机与数字媒体专业教学单位'),
    ('智能制造系', 'NCVC-MANUFACTURING', 'ACADEMIC', 50, '机电与数控专业教学单位'),
    ('交通运输系', 'NCVC-TRANSPORT', 'ACADEMIC', 60, '汽车运用与维修专业教学单位'),
    ('商贸服务系', 'NCVC-BUSINESS', 'ACADEMIC', 70, '电子商务专业教学单位'),
    ('公共基础教学部', 'NCVC-GENERAL', 'ACADEMIC', 80, '语数英及公共基础课程教学单位')
) AS definition(unit_name, unit_code, unit_type, sort_order, description)
JOIN t_organization school ON school.org_code = 'EDU-NCVC'
WHERE NOT EXISTS (
    SELECT 1
    FROM t_organization_unit existing
    WHERE existing.org_id = school.id
      AND existing.organization_unit_code = definition.unit_code
);

-- 教育行业岗位与职级。
INSERT INTO t_position (
    id, position_code, position_name, position_category, position_level,
    management, status, sort_order, description,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    item.position_code,
    item.position_name,
    item.position_category,
    item.position_level,
    item.management,
    1,
    item.sort_order,
    item.description,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('EDU-PRINCIPAL', '校长', 'MANAGEMENT', 'SCHOOL', true, 10, '学校主要负责人'),
    ('EDU-DEAN', '教务主任', 'MANAGEMENT', 'DEPARTMENT', true, 20, '教务管理岗位'),
    ('EDU-DEPT-HEAD', '系部主任', 'MANAGEMENT', 'DEPARTMENT', true, 30, '教学系部负责人'),
    ('EDU-HEAD-TEACHER', '班主任', 'TEACHING', 'CLASS', true, 40, '行政班班主任'),
    ('EDU-SUBJECT-TEACHER', '专任教师', 'TEACHING', 'SCHOOL', false, 50, '课程教学岗位'),
    ('EDU-PRACTICE-INSTRUCTOR', '实训指导教师', 'TEACHING', 'TRAINING', false, 60, '实训教学岗位'),
    ('EDU-STUDENT-AFFAIRS', '学生管理干事', 'STUDENT_AFFAIRS', 'SCHOOL', false, 70, '学生事务管理岗位'),
    ('EDU-ADMIN-STAFF', '行政职员', 'ADMINISTRATIVE', 'SCHOOL', false, 80, '学校行政岗位')
) AS item(
    position_code, position_name, position_category, position_level,
    management, sort_order, description
)
ON CONFLICT (position_code) DO UPDATE SET
    position_name = EXCLUDED.position_name,
    position_category = EXCLUDED.position_category,
    position_level = EXCLUDED.position_level,
    management = EXCLUDED.management,
    status = 1,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

INSERT INTO t_job_level (
    id, level_code, level_name, level_sequence, level_category,
    status, sort_order, description,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    item.level_code,
    item.level_name,
    item.level_sequence,
    item.level_category,
    1,
    item.sort_order,
    item.description,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('EDU-M1', '校级管理岗', 10, 'MANAGEMENT', 10, '校级领导职级'),
    ('EDU-M2', '中层管理岗', 20, 'MANAGEMENT', 20, '处室及系部管理职级'),
    ('EDU-T1', '初级教师', 30, 'PROFESSIONAL_TECHNICAL', 30, '初级专业技术职级'),
    ('EDU-T2', '中级教师', 40, 'PROFESSIONAL_TECHNICAL', 40, '中级专业技术职级'),
    ('EDU-T3', '高级教师', 50, 'PROFESSIONAL_TECHNICAL', 50, '高级专业技术职级'),
    ('EDU-A1', '行政一级', 60, 'ADMINISTRATIVE', 60, '行政教辅职级')
) AS item(
    level_code, level_name, level_sequence, level_category,
    sort_order, description
)
ON CONFLICT (level_code) DO UPDATE SET
    level_name = EXCLUDED.level_name,
    level_sequence = EXCLUDED.level_sequence,
    level_category = EXCLUDED.level_category,
    status = 1,
    sort_order = EXCLUDED.sort_order,
    description = EXCLUDED.description,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 教师人事档案。
INSERT INTO t_iam_employee (
    id, employee_code, employee_name, gender, phone, email,
    employment_status, employee_type, hire_date,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    teacher.employee_code,
    teacher.employee_name,
    teacher.gender,
    teacher.phone,
    teacher.email,
    'ACTIVE',
    'TEACHER',
    teacher.hire_date,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('T2026101', '张建国', 'MALE', '13800002101', 'zhangjg@ncvc.edu.cn', DATE '2014-09-01'),
    ('T2026102', '李晓梅', 'FEMALE', '13800002102', 'lixm@ncvc.edu.cn', DATE '2016-09-01'),
    ('T2026103', '王志强', 'MALE', '13800002103', 'wangzq@ncvc.edu.cn', DATE '2012-03-01'),
    ('T2026104', '陈雨欣', 'FEMALE', '13800002104', 'chenyx@ncvc.edu.cn', DATE '2019-09-01'),
    ('T2026105', '赵海峰', 'MALE', '13800002105', 'zhaohf@ncvc.edu.cn', DATE '2013-09-01'),
    ('T2026106', '周敏', 'FEMALE', '13800002106', 'zhoumin@ncvc.edu.cn', DATE '2017-09-01'),
    ('T2026107', '刘伟', 'MALE', '13800002107', 'liuwei@ncvc.edu.cn', DATE '2015-02-01'),
    ('T2026108', '孙丽', 'FEMALE', '13800002108', 'sunli@ncvc.edu.cn', DATE '2020-09-01'),
    ('T2026109', '黄文杰', 'MALE', '13800002109', 'huangwj@ncvc.edu.cn', DATE '2018-09-01'),
    ('T2026110', '林嘉怡', 'FEMALE', '13800002110', 'linjy@ncvc.edu.cn', DATE '2021-09-01'),
    ('T2026111', '何志远', 'MALE', '13800002111', 'hezy@ncvc.edu.cn', DATE '2011-09-01'),
    ('T2026112', '郑雅雯', 'FEMALE', '13800002112', 'zhengyw@ncvc.edu.cn', DATE '2019-02-01')
) AS teacher(employee_code, employee_name, gender, phone, email, hire_date)
ON CONFLICT (employee_code) DO UPDATE SET
    employee_name = EXCLUDED.employee_name,
    gender = EXCLUDED.gender,
    phone = EXCLUDED.phone,
    email = EXCLUDED.email,
    employment_status = 'ACTIVE',
    employee_type = 'TEACHER',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 教师任职部门。
INSERT INTO t_employee_assignment (
    id, employee_id, organization_id, organization_unit_id,
    position_id, job_level_id,
    primary_assignment, department_leader, effective_from, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    employee.id,
    school.id,
    unit.id,
    position.id,
    job_level.id,
    true,
    mapping.department_leader,
    DATE '2026-08-01',
    1,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('T2026101', 'NCVC-MANUFACTURING', 'EDU-DEPT-HEAD', 'EDU-T3', true),
    ('T2026102', 'NCVC-IT', 'EDU-DEPT-HEAD', 'EDU-T3', true),
    ('T2026103', 'NCVC-MANUFACTURING', 'EDU-PRACTICE-INSTRUCTOR', 'EDU-T2', false),
    ('T2026104', 'NCVC-IT', 'EDU-SUBJECT-TEACHER', 'EDU-T2', false),
    ('T2026105', 'NCVC-TRANSPORT', 'EDU-DEPT-HEAD', 'EDU-T3', true),
    ('T2026106', 'NCVC-BUSINESS', 'EDU-DEPT-HEAD', 'EDU-T2', true),
    ('T2026107', 'NCVC-MANUFACTURING', 'EDU-PRACTICE-INSTRUCTOR', 'EDU-T2', false),
    ('T2026108', 'NCVC-IT', 'EDU-SUBJECT-TEACHER', 'EDU-T1', false),
    ('T2026109', 'NCVC-GENERAL', 'EDU-DEPT-HEAD', 'EDU-T3', true),
    ('T2026110', 'NCVC-GENERAL', 'EDU-SUBJECT-TEACHER', 'EDU-T2', false),
    ('T2026111', 'NCVC-GENERAL', 'EDU-SUBJECT-TEACHER', 'EDU-T2', false),
    ('T2026112', 'NCVC-STUDENT-AFFAIRS', 'EDU-STUDENT-AFFAIRS', 'EDU-A1', true)
) AS mapping(employee_code, unit_code, position_code, level_code, department_leader)
JOIN t_iam_employee employee ON employee.employee_code = mapping.employee_code
JOIN t_organization school ON school.org_code = 'EDU-NCVC'
JOIN t_organization_unit unit
  ON unit.org_id = school.id
 AND unit.organization_unit_code = mapping.unit_code
JOIN t_position position ON position.position_code = mapping.position_code
JOIN t_job_level job_level ON job_level.level_code = mapping.level_code
WHERE NOT EXISTS (
    SELECT 1
    FROM t_employee_assignment existing
    WHERE existing.employee_id = employee.id
      AND existing.organization_id = school.id
      AND existing.organization_unit_id = unit.id
      AND existing.status = 1
);

-- 对脚本早期版本已经创建的任职关系补齐岗位和职级。
UPDATE t_employee_assignment assignment
SET position_id = position.id,
    job_level_id = job_level.id,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM t_iam_employee employee,
     t_position position,
     t_job_level job_level,
     (VALUES
        ('T2026101', 'EDU-DEPT-HEAD', 'EDU-T3'),
        ('T2026102', 'EDU-DEPT-HEAD', 'EDU-T3'),
        ('T2026103', 'EDU-PRACTICE-INSTRUCTOR', 'EDU-T2'),
        ('T2026104', 'EDU-SUBJECT-TEACHER', 'EDU-T2'),
        ('T2026105', 'EDU-DEPT-HEAD', 'EDU-T3'),
        ('T2026106', 'EDU-DEPT-HEAD', 'EDU-T2'),
        ('T2026107', 'EDU-PRACTICE-INSTRUCTOR', 'EDU-T2'),
        ('T2026108', 'EDU-SUBJECT-TEACHER', 'EDU-T1'),
        ('T2026109', 'EDU-DEPT-HEAD', 'EDU-T3'),
        ('T2026110', 'EDU-SUBJECT-TEACHER', 'EDU-T2'),
        ('T2026111', 'EDU-SUBJECT-TEACHER', 'EDU-T2'),
        ('T2026112', 'EDU-STUDENT-AFFAIRS', 'EDU-A1')
     ) AS mapping(employee_code, position_code, level_code)
WHERE assignment.employee_id = employee.id
  AND employee.employee_code = mapping.employee_code
  AND position.position_code = mapping.position_code
  AND job_level.level_code = mapping.level_code
  AND assignment.status = 1;

-- 教师教务档案。
INSERT INTO edu_teacher_profile (
    id, employee_id, teacher_no, teacher_name, department_id,
    specialty, max_weekly_lessons, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    employee.id,
    employee.employee_code,
    employee.employee_name,
    unit.id,
    mapping.specialty,
    mapping.max_lessons,
    true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('T2026101', 'NCVC-MANUFACTURING', '电气控制与PLC', 18),
    ('T2026102', 'NCVC-IT', '软件开发与数据库', 18),
    ('T2026103', 'NCVC-MANUFACTURING', '数控加工技术', 20),
    ('T2026104', 'NCVC-IT', '计算机网络技术', 20),
    ('T2026105', 'NCVC-TRANSPORT', '汽车检测与维修', 18),
    ('T2026106', 'NCVC-BUSINESS', '电子商务运营', 20),
    ('T2026107', 'NCVC-MANUFACTURING', '机械制图与CAD', 20),
    ('T2026108', 'NCVC-IT', '数字媒体技术', 20),
    ('T2026109', 'NCVC-GENERAL', '语文', 18),
    ('T2026110', 'NCVC-GENERAL', '数学', 20),
    ('T2026111', 'NCVC-GENERAL', '英语', 20),
    ('T2026112', 'NCVC-STUDENT-AFFAIRS', '德育与职业生涯规划', 16)
) AS mapping(employee_code, unit_code, specialty, max_lessons)
JOIN t_iam_employee employee ON employee.employee_code = mapping.employee_code
JOIN t_organization school ON school.org_code = 'EDU-NCVC'
JOIN t_organization_unit unit
  ON unit.org_id = school.id
 AND unit.organization_unit_code = mapping.unit_code
ON CONFLICT (employee_id) DO UPDATE SET
    teacher_no = EXCLUDED.teacher_no,
    teacher_name = EXCLUDED.teacher_name,
    department_id = EXCLUDED.department_id,
    specialty = EXCLUDED.specialty,
    max_weekly_lessons = EXCLUDED.max_weekly_lessons,
    enabled = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 学年学期。
UPDATE edu_academic_term
SET current_term = false,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE current_term = true
  AND term_code <> '2026-2027-1';

INSERT INTO edu_academic_term (
    id, term_code, term_name, academic_year, term_no,
    start_date, end_date, week_count, current_term, status,
    create_by, create_time, last_update_by, last_update_time
)
VALUES
    (gen_random_uuid()::text, '2025-2026-2', '2025—2026学年第二学期', '2025-2026', 2, DATE '2026-02-23', DATE '2026-07-10', 20, false, 'FINISHED', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP),
    (gen_random_uuid()::text, '2026-2027-1', '2026—2027学年第一学期', '2026-2027', 1, DATE '2026-09-01', DATE '2027-01-22', 20, true, 'ACTIVE', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP),
    (gen_random_uuid()::text, '2026-2027-2', '2026—2027学年第二学期', '2026-2027', 2, DATE '2027-02-22', DATE '2027-07-09', 20, false, 'PLANNED', 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP)
ON CONFLICT (term_code) DO UPDATE SET
    term_name = EXCLUDED.term_name,
    academic_year = EXCLUDED.academic_year,
    term_no = EXCLUDED.term_no,
    start_date = EXCLUDED.start_date,
    end_date = EXCLUDED.end_date,
    week_count = EXCLUDED.week_count,
    current_term = EXCLUDED.current_term,
    status = EXCLUDED.status,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 中职专业。
INSERT INTO edu_major (
    id, major_code, major_name, schooling_years, department_id, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    definition.major_code,
    definition.major_name,
    3,
    unit.id,
    true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('710201', '计算机应用', 'NCVC-IT'),
    ('710204', '数字媒体技术应用', 'NCVC-IT'),
    ('660301', '机电技术应用', 'NCVC-MANUFACTURING'),
    ('660103', '数控技术应用', 'NCVC-MANUFACTURING'),
    ('700206', '汽车运用与维修', 'NCVC-TRANSPORT'),
    ('730701', '电子商务', 'NCVC-BUSINESS')
) AS definition(major_code, major_name, unit_code)
JOIN t_organization school ON school.org_code = 'EDU-NCVC'
JOIN t_organization_unit unit
  ON unit.org_id = school.id
 AND unit.organization_unit_code = definition.unit_code
ON CONFLICT (major_code) DO UPDATE SET
    major_name = EXCLUDED.major_name,
    schooling_years = EXCLUDED.schooling_years,
    department_id = EXCLUDED.department_id,
    enabled = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 2026级行政班，每个专业一个班。
INSERT INTO edu_administrative_class (
    id, class_code, class_name, grade_year, major_id,
    head_teacher_id, campus_id, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    definition.class_code,
    definition.class_name,
    2026,
    major.id,
    teacher.id,
    campus.id,
    'ACTIVE',
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('2026-JS-01', '2026级计算机应用1班', '710201', 'T2026102', 'EDU-NCVC-MAIN'),
    ('2026-SM-01', '2026级数字媒体技术应用1班', '710204', 'T2026108', 'EDU-NCVC-MAIN'),
    ('2026-JD-01', '2026级机电技术应用1班', '660301', 'T2026101', 'EDU-NCVC-TRAIN'),
    ('2026-SK-01', '2026级数控技术应用1班', '660103', 'T2026103', 'EDU-NCVC-TRAIN'),
    ('2026-QX-01', '2026级汽车运用与维修1班', '700206', 'T2026105', 'EDU-NCVC-TRAIN'),
    ('2026-DS-01', '2026级电子商务1班', '730701', 'T2026106', 'EDU-NCVC-MAIN')
) AS definition(class_code, class_name, major_code, teacher_code, campus_code)
JOIN edu_major major ON major.major_code = definition.major_code
JOIN t_iam_employee teacher ON teacher.employee_code = definition.teacher_code
JOIN t_organization campus ON campus.org_code = definition.campus_code
ON CONFLICT (class_code) DO UPDATE SET
    class_name = EXCLUDED.class_name,
    grade_year = EXCLUDED.grade_year,
    major_id = EXCLUDED.major_id,
    head_teacher_id = EXCLUDED.head_teacher_id,
    campus_id = EXCLUDED.campus_id,
    status = 'ACTIVE',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 公共基础课与专业核心课程。
INSERT INTO edu_course_catalog (
    id, course_code, course_name, course_category, course_nature,
    total_hours, theory_hours, practice_hours, credits,
    required_room_type, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    course.course_code,
    course.course_name,
    course.course_category,
    course.course_nature,
    course.total_hours,
    course.theory_hours,
    course.practice_hours,
    course.credits,
    course.room_type,
    true,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM (VALUES
    ('GG101', '语文', 'GENERAL', 'REQUIRED', 144, 120, 24, 8.0, 'STANDARD'),
    ('GG102', '数学', 'GENERAL', 'REQUIRED', 144, 120, 24, 8.0, 'STANDARD'),
    ('GG103', '英语', 'GENERAL', 'REQUIRED', 108, 90, 18, 6.0, 'STANDARD'),
    ('GG104', '中国特色社会主义', 'GENERAL', 'REQUIRED', 36, 30, 6, 2.0, 'STANDARD'),
    ('GG105', '信息技术', 'GENERAL', 'REQUIRED', 72, 24, 48, 4.0, 'COMPUTER'),
    ('JS101', 'Python程序设计', 'PROFESSIONAL', 'REQUIRED', 108, 36, 72, 6.0, 'COMPUTER'),
    ('JS102', '数据库应用基础', 'PROFESSIONAL', 'REQUIRED', 72, 24, 48, 4.0, 'COMPUTER'),
    ('JS202', '网络组建与维护', 'PROFESSIONAL', 'REQUIRED', 108, 36, 72, 6.0, 'LAB'),
    ('SM101', '短视频拍摄与制作', 'PROFESSIONAL', 'REQUIRED', 108, 24, 84, 6.0, 'COMPUTER'),
    ('SM102', '图形图像处理', 'PROFESSIONAL', 'REQUIRED', 108, 24, 84, 6.0, 'COMPUTER'),
    ('JD101', '电工基础', 'PROFESSIONAL', 'REQUIRED', 108, 54, 54, 6.0, 'LAB'),
    ('JD201', 'PLC控制技术', 'PROFESSIONAL', 'REQUIRED', 108, 36, 72, 6.0, 'LAB'),
    ('SK201', '数控车削编程与加工', 'PROFESSIONAL', 'REQUIRED', 144, 36, 108, 8.0, 'SPECIAL'),
    ('QX101', '汽车发动机构造与维修', 'PROFESSIONAL', 'REQUIRED', 144, 48, 96, 8.0, 'SPECIAL'),
    ('DS101', '网店运营实务', 'PROFESSIONAL', 'REQUIRED', 108, 36, 72, 6.0, 'COMPUTER'),
    ('DS102', '直播电商实务', 'PROFESSIONAL', 'ELECTIVE', 72, 18, 54, 4.0, 'COMPUTER')
) AS course(
    course_code, course_name, course_category, course_nature,
    total_hours, theory_hours, practice_hours, credits, room_type
)
ON CONFLICT (course_code) DO UPDATE SET
    course_name = EXCLUDED.course_name,
    course_category = EXCLUDED.course_category,
    course_nature = EXCLUDED.course_nature,
    total_hours = EXCLUDED.total_hours,
    theory_hours = EXCLUDED.theory_hours,
    practice_hours = EXCLUDED.practice_hours,
    credits = EXCLUDED.credits,
    required_room_type = EXCLUDED.required_room_type,
    enabled = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

-- 生成 60 名学生，每个行政班 10 人。
WITH student_source AS (
    SELECT
        sequence_no,
        '2026' || lpad(sequence_no::text, 4, '0') AS student_no,
        (ARRAY['陈','林','黄','张','李','王','刘','周','吴','郑'])[((sequence_no - 1) % 10) + 1]
            || (ARRAY['子轩','雨桐','浩然','思琪','宇航','欣怡'])[((sequence_no - 1) % 6) + 1]
            || sequence_no::text AS student_name,
        CASE WHEN sequence_no % 2 = 0 THEN 'FEMALE' ELSE 'MALE' END AS gender,
        (ARRAY[
            '2026-JS-01', '2026-SM-01', '2026-JD-01',
            '2026-SK-01', '2026-QX-01', '2026-DS-01'
        ])[((sequence_no - 1) / 10) + 1] AS class_code
    FROM generate_series(1, 60) AS sequence_no
)
INSERT INTO edu_student_profile (
    id, student_no, student_name, gender, grade_year,
    major_id, administrative_class_id, enrollment_status, phone,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    source.student_no,
    source.student_name,
    source.gender,
    2026,
    class.major_id,
    class.id,
    'ACTIVE',
    '139' || lpad(source.sequence_no::text, 8, '0'),
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM student_source source
JOIN edu_administrative_class class ON class.class_code = source.class_code
ON CONFLICT (student_no) DO UPDATE SET
    student_name = EXCLUDED.student_name,
    gender = EXCLUDED.gender,
    grade_year = EXCLUDED.grade_year,
    major_id = EXCLUDED.major_id,
    administrative_class_id = EXCLUDED.administrative_class_id,
    enrollment_status = 'ACTIVE',
    phone = EXCLUDED.phone,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

COMMIT;
