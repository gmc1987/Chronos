-- 教育行业菜单原子操作权限。
-- 目标数据库：ChronosEducation。脚本可重复执行。

BEGIN;

CREATE TEMP TABLE tmp_education_menu_permission (
    menu_name varchar(200) NOT NULL,
    permission_prefix varchar(160) NOT NULL,
    permission_label varchar(200) NOT NULL,
    permission_profile varchar(32) NOT NULL
) ON COMMIT DROP;

INSERT INTO tmp_education_menu_permission VALUES
    ('场地管理', 'education:resource:venue', '场地', 'CRUD'),
    ('教师工作台', 'education:teacher:workbench', '教师工作台', 'READ'),
    ('教师业务', 'education:teacher:business', '教师业务', 'CRUD'),
    ('学生档案', 'education:student', '学生档案', 'CRUD_IMPORT'),
    ('班级管理', 'education:class', '班级', 'CRUD_IMPORT'),
    ('教学计划', 'education:teaching:plan', '教学计划', 'CRUD'),
    ('教案管理', 'education:teaching:lesson-plan', '教案', 'CRUD'),
    ('备课管理', 'education:teaching:preparation', '备课', 'CRUD'),
    ('课件管理', 'education:teaching:courseware', '课件', 'CRUD'),
    ('作业管理', 'education:teaching:homework', '作业', 'CRUD'),
    ('题库维护', 'education:teaching:question-bank', '题库', 'CRUD_IMPORT'),
    ('知识点维护', 'education:teaching:knowledge-point', '知识点', 'CRUD_IMPORT'),
    ('教研管理', 'education:teaching:research', '教研', 'CRUD'),
    ('错题维护', 'education:teaching:error-book', '错题', 'CRUD'),
    ('班级作业管理', 'education:homework:class', '班级作业', 'CRUD'),
    ('作业统计&分析', 'education:homework:analysis', '作业统计分析', 'ANALYSIS'),
    ('家长管理', 'education:home-school:parent', '家长', 'CRUD_IMPORT'),
    ('班级群管理', 'education:home-school:group', '班级群', 'CRUD'),
    ('学校通知', 'education:home-school:notice', '家校通知', 'CRUD'),
    ('家长反馈', 'education:home-school:feedback', '家长反馈', 'CRUD'),
    ('家长会管理', 'education:home-school:meeting', '家长会', 'CRUD'),
    ('家校沟通记录', 'education:home-school:communication', '家校沟通记录', 'READ_EXPORT'),
    ('考试计划管理', 'education:exam:plan', '考试计划', 'CRUD'),
    ('监考排班', 'education:exam:invigilation', '监考排班', 'CRUD'),
    ('考场管理', 'education:exam:room', '考场', 'CRUD'),
    ('试卷分析', 'education:exam:paper-analysis', '试卷分析', 'ANALYSIS'),
    ('成绩管理', 'education:score', '成绩', 'CRUD_IMPORT'),
    ('班级分析', 'education:score:class-analysis', '班级成绩分析', 'ANALYSIS'),
    ('年级分析', 'education:score:grade-analysis', '年级成绩分析', 'ANALYSIS'),
    ('学科分析', 'education:score:subject-analysis', '学科成绩分析', 'ANALYSIS'),
    ('趋势分析', 'education:score:trend-analysis', '成绩趋势分析', 'ANALYSIS'),
    ('知识点分析', 'education:score:knowledge-analysis', '知识点成绩分析', 'ANALYSIS'),
    ('会议管理', 'education:meeting', '会议', 'CRUD'),
    ('会议室维护', 'education:meeting:room', '会议室', 'CRUD'),
    ('AI模型管理', 'education:ai:model', 'AI模型', 'CRUD'),
    ('AI能力授权', 'education:ai:authorization', 'AI能力授权', 'CRUD'),
    ('智能体能力配置', 'education:ai:agent-capability', '智能体能力配置', 'CRUD'),
    ('AI知识库', 'education:ai:knowledge', 'AI知识库', 'CRUD_IMPORT'),
    ('学期管理', 'education:term', '学期', 'CRUD'),
    ('年级管理', 'education:grade', '年级', 'CRUD'),
    ('学科管理', 'education:subject', '学科', 'CRUD'),
    ('课程管理', 'education:course', '课程', 'CRUD_IMPORT'),
    ('教师任教关系', 'education:teaching-assignment', '教师任教关系', 'CRUD'),
    ('走班排课', 'education:scheduling', '走班排课', 'CRUD');

-- 同名菜单可能出现在不同业务域，只匹配教育功能根节点下的后代菜单。
WITH RECURSIVE education_roots AS (
    SELECT id
    FROM t_menu
    WHERE menu_name IN (
        '资源中心', '教师中心', '学生中心', '班级中心', '教学中心',
        '作业中心', 'AI智能中心', '家校中心', '考试中心', '成绩中心',
        '会议中心', '教务中心'
    )
), education_menus AS (
    SELECT menu.id, menu.menu_name
    FROM t_menu menu
    JOIN education_roots root ON root.id = menu.id
    UNION ALL
    SELECT child.id, child.menu_name
    FROM t_menu child
    JOIN education_menus parent ON child.parent_id = parent.id
), action_catalog AS (
    SELECT * FROM (VALUES
        ('VIEW', 'view', '查看'),
        ('CREATE', 'create', '新增'),
        ('UPDATE', 'update', '修改'),
        ('DELETE', 'delete', '删除'),
        ('MANAGE', 'manage', '管理'),
        ('IMPORT', 'import', '导入'),
        ('EXPORT', 'export', '导出'),
        ('PRIVACY_VIEW', 'privacy:view', '查看敏感字段')
    ) AS actions(action_type, action_code, action_name)
), desired AS (
    SELECT
        menu.id AS menu_id,
        definition.permission_prefix || ':' || action.action_code AS permission_code,
        action.action_name || definition.permission_label AS permission_name,
        action.action_type
    FROM education_menus menu
    JOIN tmp_education_menu_permission definition ON definition.menu_name = menu.menu_name
    CROSS JOIN action_catalog action
    WHERE
        (definition.permission_profile = 'CRUD'
            AND action.action_type IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'MANAGE'))
        OR (definition.permission_profile = 'CRUD_IMPORT'
            AND action.action_type IN ('VIEW', 'CREATE', 'UPDATE', 'DELETE', 'MANAGE', 'IMPORT', 'EXPORT'))
        OR (definition.permission_profile = 'READ'
            AND action.action_type = 'VIEW')
        OR (definition.permission_profile = 'READ_EXPORT'
            AND action.action_type IN ('VIEW', 'EXPORT'))
        OR (definition.permission_profile = 'ANALYSIS'
            AND action.action_type IN ('VIEW', 'EXPORT'))
        OR (definition.permission_prefix = 'education:student'
            AND action.action_type = 'PRIVACY_VIEW')
)
INSERT INTO t_permission (
    id, create_by, create_time, last_update_by, last_update_time,
    permission_name, permission_code, permission_type, menu_id,
    action_type, resource_type, scope_type, status, built_in, description
)
SELECT
    gen_random_uuid()::text,
    'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
    desired.permission_name,
    desired.permission_code,
    'MENU_ACTION',
    desired.menu_id,
    desired.action_type,
    'MENU',
    'ROLE',
    1,
    true,
    '教育行业模板菜单原子操作权限'
FROM desired
ON CONFLICT (permission_code) DO UPDATE SET
    permission_name = EXCLUDED.permission_name,
    permission_type = 'MENU_ACTION',
    menu_id = EXCLUDED.menu_id,
    action_type = EXCLUDED.action_type,
    resource_type = 'MENU',
    status = 1,
    built_in = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;

COMMIT;
