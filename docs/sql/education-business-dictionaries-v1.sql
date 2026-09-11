-- 教育行业业务下拉字典 V1。
-- 目标数据库：ChronosEducation。父项和子项使用同一 dict_code，脚本可重复执行。

BEGIN;

SELECT pg_advisory_xact_lock(hashtext('education-business-dictionaries-v1'));

CREATE TEMP TABLE tmp_dictionary_definition (
    dict_code varchar(100) NOT NULL,
    root_name varchar(200) NOT NULL,
    item_value varchar(200) NOT NULL,
    item_name varchar(200) NOT NULL,
    sort_order integer NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_dictionary_definition VALUES
    ('COMMON_GENDER', '性别', 'MALE', '男', 10),
    ('COMMON_GENDER', '性别', 'FEMALE', '女', 20),
    ('COMMON_GENDER', '性别', 'UNSPECIFIED', '未说明', 30),
    ('COMMON_ENABLE_STATUS', '启停状态', '1', '启用', 10),
    ('COMMON_ENABLE_STATUS', '启停状态', '0', '停用', 20),
    ('IAM_ORGANIZATION_TYPE', '机构类型', 'EDUCATION_GROUP', '教育集团', 10),
    ('IAM_ORGANIZATION_TYPE', '机构类型', 'SCHOOL', '学校', 20),
    ('IAM_ORGANIZATION_TYPE', '机构类型', 'CAMPUS', '校区', 30),
    ('IAM_ORGANIZATION_TYPE', '机构类型', 'COLLEGE', '学院', 40),
    ('IAM_ORGANIZATION_TYPE', '机构类型', 'DEPARTMENT', '系部', 50),
    ('IAM_DEPARTMENT_TYPE', '部门类型', 'ADMINISTRATIVE', '行政部门', 10),
    ('IAM_DEPARTMENT_TYPE', '部门类型', 'ACADEMIC', '教学单位', 20),
    ('IAM_DEPARTMENT_TYPE', '部门类型', 'RESEARCH', '科研机构', 30),
    ('IAM_DEPARTMENT_TYPE', '部门类型', 'SUPPORT', '教辅单位', 40),
    ('IAM_DEPARTMENT_TYPE', '部门类型', 'DEPARTMENT', '其他单位', 50),
    ('IAM_POSITION_CATEGORY', '岗位类别', 'TEACHING', '教学', 10),
    ('IAM_POSITION_CATEGORY', '岗位类别', 'RESEARCH', '科研', 20),
    ('IAM_POSITION_CATEGORY', '岗位类别', 'MANAGEMENT', '管理', 30),
    ('IAM_POSITION_CATEGORY', '岗位类别', 'ADMINISTRATIVE', '行政', 40),
    ('IAM_POSITION_CATEGORY', '岗位类别', 'STUDENT_AFFAIRS', '学生工作', 50),
    ('IAM_POSITION_CATEGORY', '岗位类别', 'LOGISTICS', '后勤', 60),
    ('IAM_JOB_LEVEL_CATEGORY', '职级序列', 'MANAGEMENT', '管理序列', 10),
    ('IAM_JOB_LEVEL_CATEGORY', '职级序列', 'PROFESSIONAL_TECHNICAL', '专业技术序列', 20),
    ('IAM_JOB_LEVEL_CATEGORY', '职级序列', 'ADMINISTRATIVE', '行政教辅序列', 30),
    ('IAM_EMPLOYEE_TYPE', '员工类型', 'TEACHER', '专任教师', 10),
    ('IAM_EMPLOYEE_TYPE', '员工类型', 'STAFF', '行政教职工', 20),
    ('IAM_EMPLOYEE_TYPE', '员工类型', 'CONTRACT', '合同人员', 30),
    ('IAM_EMPLOYEE_TYPE', '员工类型', 'VISITING', '外聘/进修人员', 40),
    ('IAM_EMPLOYEE_TYPE', '员工类型', 'INTERN', '实习人员', 50),
    ('IAM_EMPLOYMENT_STATUS', '在职状态', 'ACTIVE', '在职', 10),
    ('IAM_EMPLOYMENT_STATUS', '在职状态', 'SUSPENDED', '停职', 20),
    ('IAM_EMPLOYMENT_STATUS', '在职状态', 'LEFT', '离职', 30),
    ('EDU_COURSE_CATEGORY', '课程类别', 'GENERAL', '公共基础课', 10),
    ('EDU_COURSE_CATEGORY', '课程类别', 'PROFESSIONAL', '专业课', 20),
    ('EDU_COURSE_CATEGORY', '课程类别', 'PRACTICE', '实训课', 30),
    ('EDU_COURSE_NATURE', '课程性质', 'REQUIRED', '必修', 10),
    ('EDU_COURSE_NATURE', '课程性质', 'ELECTIVE', '选修', 20),
    ('EDU_STUDENT_STATUS', '学籍状态', 'ACTIVE', '在籍', 10),
    ('EDU_STUDENT_STATUS', '学籍状态', 'SUSPENDED', '休学', 20),
    ('EDU_STUDENT_STATUS', '学籍状态', 'GRADUATED', '毕业', 30),
    ('EDU_STUDENT_STATUS', '学籍状态', 'WITHDRAWN', '退学', 40),
    ('EDU_ROOM_TYPE', '教室类型', 'STANDARD', '普通教室', 10),
    ('EDU_ROOM_TYPE', '教室类型', 'LAB', '实验室', 20),
    ('EDU_ROOM_TYPE', '教室类型', 'COMPUTER', '计算机房', 30),
    ('EDU_ROOM_TYPE', '教室类型', 'SPECIAL', '专业实训室', 40),
    ('EDU_LEAVE_TYPE', '请假类型', 'SICK', '病假', 10),
    ('EDU_LEAVE_TYPE', '请假类型', 'PERSONAL', '事假', 20),
    ('EDU_LEAVE_TYPE', '请假类型', 'OFFICIAL', '公假', 30),
    ('EDU_COURSE_ADJUSTMENT_TYPE', '调课类型', 'MOVE', '调课', 10),
    ('EDU_COURSE_ADJUSTMENT_TYPE', '调课类型', 'SUBSTITUTE', '代课', 20),
    ('EDU_COURSE_ADJUSTMENT_TYPE', '调课类型', 'CANCEL', '停课', 30),
    ('EDU_COURSE_ADJUSTMENT_TYPE', '调课类型', 'MAKEUP', '补课', 40);

-- 字典根节点。
INSERT INTO t_dict (
    id, dict_code, dict_name, dict_value, parent_id, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    definition.dict_code,
    min(definition.root_name),
    NULL,
    NULL,
    1,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_dictionary_definition definition
WHERE NOT EXISTS (
    SELECT 1
    FROM t_dict existing
    WHERE existing.dict_code = definition.dict_code
      AND existing.parent_id IS NULL
)
GROUP BY definition.dict_code;

-- 字典选项。dict_value 是业务提交值，dict_name 是界面显示名称。
INSERT INTO t_dict (
    id, dict_code, dict_name, dict_value, parent_id, status,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    definition.dict_code,
    definition.item_name,
    definition.item_value,
    root.id,
    1,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP
FROM tmp_dictionary_definition definition
JOIN t_dict root
  ON root.dict_code = definition.dict_code
 AND root.parent_id IS NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM t_dict existing
    WHERE existing.dict_code = definition.dict_code
      AND existing.dict_value = definition.item_value
      AND existing.parent_id IS NOT NULL
);

-- 对已存在项同步名称并重新启用。
UPDATE t_dict existing
SET dict_name = definition.item_name,
    status = 1,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
FROM tmp_dictionary_definition definition
WHERE existing.dict_code = definition.dict_code
  AND existing.dict_value = definition.item_value
  AND existing.parent_id IS NOT NULL;

COMMIT;
