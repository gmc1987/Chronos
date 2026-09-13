-- 智慧校园第一阶段 20 项 MVP 数据库就绪度检查。
-- 只读脚本：不会创建、更新或删除任何业务数据，可在初始化、迁库和发布前重复执行。

WITH checks(capability_no, capability, passed, evidence) AS (
    SELECT 1, '用户',
           EXISTS (SELECT 1 FROM t_admin_user WHERE status = 1),
           'active_users=' || (SELECT count(*) FROM t_admin_user WHERE status = 1)
    UNION ALL
    SELECT 2, '组织',
           to_regclass('public.t_organization') IS NOT NULL
               AND to_regclass('public.t_organization_unit') IS NOT NULL
               AND to_regclass('public.t_position') IS NOT NULL
               AND to_regclass('public.t_job_level') IS NOT NULL,
           'organization/unit/position/job_level tables'
    UNION ALL
    SELECT 3, '权限',
           EXISTS (SELECT 1 FROM t_permission WHERE status = 1)
               AND to_regclass('public.t_role_permission') IS NOT NULL
               AND to_regclass('public.t_role_data_scope') IS NOT NULL,
           'active_permissions=' || (SELECT count(*) FROM t_permission WHERE status = 1)
    UNION ALL
    SELECT 4, '教师',
           EXISTS (SELECT 1 FROM edu_teacher_profile WHERE enabled = true),
           'enabled_teachers=' || (SELECT count(*) FROM edu_teacher_profile WHERE enabled = true)
    UNION ALL
    SELECT 5, '学生',
           EXISTS (SELECT 1 FROM edu_student_profile WHERE enrollment_status = 'ACTIVE'),
           'active_students=' || (SELECT count(*) FROM edu_student_profile WHERE enrollment_status = 'ACTIVE')
    UNION ALL
    SELECT 6, '班级',
           EXISTS (SELECT 1 FROM edu_administrative_class WHERE status = 'ACTIVE')
               AND EXISTS (SELECT 1 FROM edu_teaching_class_member),
           'classes=' || (SELECT count(*) FROM edu_administrative_class WHERE status = 'ACTIVE')
               || ', teaching_members=' || (SELECT count(*) FROM edu_teaching_class_member)
    UNION ALL
    SELECT 7, '课程',
           EXISTS (SELECT 1 FROM edu_course_catalog WHERE enabled = true)
               AND EXISTS (SELECT 1 FROM edu_course_offering),
           'courses=' || (SELECT count(*) FROM edu_course_catalog WHERE enabled = true)
               || ', offerings=' || (SELECT count(*) FROM edu_course_offering)
    UNION ALL
    SELECT 8, 'Form Engine',
           EXISTS (SELECT 1 FROM form_definition WHERE status = 'PUBLISHED')
               AND EXISTS (SELECT 1 FROM form_field),
           'published_forms=' || (SELECT count(*) FROM form_definition WHERE status = 'PUBLISHED')
               || ', fields=' || (SELECT count(*) FROM form_field)
    UNION ALL
    SELECT 9, 'Workflow Engine',
           (SELECT count(*) FROM wf_definition
            WHERE status = 'PUBLISHED'
              AND flowable_deployment_id IS NOT NULL) >= 3
               AND EXISTS (SELECT 1 FROM wf_node)
               AND EXISTS (SELECT 1 FROM wf_edge),
           'deployed_workflows=' || (
               SELECT count(*) FROM wf_definition
               WHERE status = 'PUBLISHED' AND flowable_deployment_id IS NOT NULL
           )
    UNION ALL
    SELECT 10, '待办',
           to_regclass('public.wf_task') IS NOT NULL
               AND to_regclass('public.wf_task_candidate') IS NOT NULL
               AND EXISTS (
                   SELECT 1 FROM t_permission
                   WHERE permission_code = 'workflow:instance:view' AND status = 1
               ),
           'task and candidate projection with workflow:instance:view'
    UNION ALL
    SELECT 11, '请假',
           EXISTS (
               SELECT 1 FROM wf_definition
               WHERE flow_code = 'EDU_TEACHER_LEAVE_APPROVAL' AND status = 'PUBLISHED'
           )
               AND EXISTS (
                   SELECT 1 FROM wf_definition
                   WHERE flow_code = 'EDU_STUDENT_LEAVE_APPROVAL' AND status = 'PUBLISHED'
               )
               AND to_regclass('public.edu_leave_request') IS NOT NULL,
           'teacher and student leave workflows with business ledger'
    UNION ALL
    SELECT 12, '调课',
           EXISTS (
               SELECT 1 FROM wf_definition
               WHERE flow_code = 'EDU_COURSE_ADJUSTMENT_APPROVAL' AND status = 'PUBLISHED'
           )
               AND to_regclass('public.edu_course_adjustment_record') IS NOT NULL,
           'course adjustment workflow with recovery ledger'
    UNION ALL
    SELECT 13, '基础排课',
           EXISTS (SELECT 1 FROM edu_schedule_entry)
               AND to_regclass('public.edu_schedule_plan_version') IS NOT NULL
               AND to_regclass('public.edu_schedule_candidate_plan') IS NOT NULL,
           'schedule_entries=' || (SELECT count(*) FROM edu_schedule_entry)
    UNION ALL
    SELECT 14, '通知',
           to_regclass('public.msg_publication') IS NOT NULL
               AND to_regclass('public.msg_publication_recipient') IS NOT NULL
               AND to_regclass('public.wf_outbox') IS NOT NULL,
           'publication, recipient and reliable outbox tables'
    UNION ALL
    SELECT 15, '文件',
           to_regclass('public.t_managed_file') IS NOT NULL,
           'managed_files=' || (SELECT count(*) FROM t_managed_file)
    UNION ALL
    SELECT 16, 'AI助手',
           EXISTS (
               SELECT 1 FROM t_permission
               WHERE permission_code = 'education:ai:assistant:use' AND status = 1
           )
               AND to_regclass('public.kb_document_chunk') IS NOT NULL,
           'assistant permission and knowledge retrieval index'
    UNION ALL
    SELECT 17, 'Knowledge Base',
           EXISTS (SELECT 1 FROM kb_knowledge_base WHERE enabled = true)
               AND EXISTS (SELECT 1 FROM kb_document)
               AND EXISTS (SELECT 1 FROM kb_document_chunk),
           'bases=' || (SELECT count(*) FROM kb_knowledge_base WHERE enabled = true)
               || ', documents=' || (SELECT count(*) FROM kb_document)
               || ', chunks=' || (SELECT count(*) FROM kb_document_chunk)
    UNION ALL
    SELECT 18, 'Scheduling Agent',
           to_regclass('public.edu_scheduling_agent_proposal') IS NOT NULL
               AND EXISTS (
                   SELECT 1
                   FROM t_role role
                   JOIN t_role_permission role_permission
                     ON role_permission.role_id = role.id
                   JOIN t_permission permission
                     ON permission.id = role_permission.permission_id
                   WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
                     AND role.status = 1
                     AND permission.permission_code = 'education:ai:agent:use'
                     AND permission.status = 1
               )
               AND EXISTS (
                   SELECT 1
                   FROM t_role role
                   JOIN t_role_permission role_permission
                     ON role_permission.role_id = role.id
                   JOIN t_permission permission
                     ON permission.id = role_permission.permission_id
                   WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
                     AND role.status = 1
                     AND permission.permission_code = 'education:ai:agent:confirm'
                     AND permission.status = 1
               ),
           'controlled proposal table with academic role use and confirm permissions'
    UNION ALL
    SELECT 19, 'AI教务Agent',
           EXISTS (
               SELECT 1
               FROM t_role role
               JOIN t_role_permission role_permission
                 ON role_permission.role_id = role.id
               JOIN t_permission permission
                 ON permission.id = role_permission.permission_id
               WHERE role.role_code = 'EDU_ACADEMIC_APPROVER'
                 AND role.status = 1
                 AND permission.permission_code = 'education:ai:agent:use'
                 AND permission.status = 1
           )
               AND to_regclass('public.edu_teacher_time_constraint') IS NOT NULL,
           'academic role analysis permission and constraint data'
    UNION ALL
    SELECT 20, '审计',
           to_regclass('public.t_audit_log') IS NOT NULL
               AND EXISTS (
                   SELECT 1 FROM t_permission
                   WHERE permission_code = 'iam:audit:view' AND status = 1
               ),
           'audit_rows=' || (SELECT count(*) FROM t_audit_log)
)
SELECT capability_no,
       capability,
       CASE WHEN passed THEN 'PASS' ELSE 'FAIL' END AS result,
       evidence,
       count(*) FILTER (WHERE passed) OVER () AS passed_count,
       count(*) FILTER (WHERE NOT passed) OVER () AS failed_count,
       count(*) OVER () AS total_count
FROM checks
ORDER BY capability_no;
