CREATE TABLE IF NOT EXISTS public.edu_parent_feedback (
    id varchar(64) PRIMARY KEY,
    parent_id varchar(64) NOT NULL,
    student_id varchar(64) NOT NULL,
    class_id varchar(64) NOT NULL,
    title varchar(200) NOT NULL,
    content text NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'SUBMITTED',
    assigned_to varchar(100),
    due_at timestamp,
    accepted_at timestamp,
    resolved_at timestamp,
    closed_at timestamp,
    parent_confirmed_at timestamp,
    reopened_at timestamp,
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp
);
CREATE INDEX IF NOT EXISTS idx_edu_parent_feedback_parent
    ON public.edu_parent_feedback (parent_id, create_time);
CREATE INDEX IF NOT EXISTS idx_edu_parent_feedback_class_status
    ON public.edu_parent_feedback (class_id, status, due_at);

CREATE TABLE IF NOT EXISTS public.edu_communication_record (
    id varchar(64) PRIMARY KEY,
    teacher_username varchar(100) NOT NULL,
    student_id varchar(64) NOT NULL,
    class_id varchar(64) NOT NULL,
    channel varchar(32) NOT NULL,
    subject varchar(200),
    content text NOT NULL,
    sensitive_content text,
    occurred_at timestamp NOT NULL,
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp
);
CREATE INDEX IF NOT EXISTS idx_edu_communication_student
    ON public.edu_communication_record (student_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_edu_communication_class
    ON public.edu_communication_record (class_id, occurred_at);

DO $$
DECLARE menu_id varchar(64);
BEGIN
    SELECT id INTO menu_id FROM public.t_menu WHERE menu_name = '家校中心' LIMIT 1;
    IF menu_id IS NOT NULL THEN
        INSERT INTO public.t_permission
            (id, create_by, create_time, last_update_by, last_update_time,
             permission_name, permission_code, permission_type, menu_id,
             action_type, resource_type, scope_type, status, built_in, description)
        SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
               v.label, v.code, 'MENU_ACTION', menu_id, v.action, 'MENU', 'ROLE', 1, true,
               '家长反馈与家校沟通记录'
        FROM (VALUES
            ('查看家长反馈','education:home-school:feedback:view','VIEW'),
            ('提交家长反馈','education:home-school:feedback:create','CREATE'),
            ('处理家长反馈','education:home-school:feedback:update','UPDATE'),
            ('确认或重开家长反馈','education:home-school:feedback:confirm','UPDATE'),
            ('查看沟通记录','education:home-school:communication:view','VIEW'),
            ('记录家校沟通','education:home-school:communication:create','CREATE'),
            ('查看敏感沟通字段','education:home-school:communication:sensitive','VIEW'),
            ('导出沟通记录','education:home-school:communication:export','EXPORT')
        ) v(label, code, action)
        WHERE NOT EXISTS (SELECT 1 FROM public.t_permission p WHERE p.permission_code = v.code);
    END IF;
END $$;
