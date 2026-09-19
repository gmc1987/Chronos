-- 家校中心第一交付切片。所有对象和权限均可重复执行。
CREATE TABLE IF NOT EXISTS public.edu_parent_account_binding (
    id varchar(64) PRIMARY KEY,
    parent_id varchar(64) NOT NULL,
    username varchar(100) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    verified_at timestamp,
    invalidated_at timestamp,
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_parent_account_binding UNIQUE (parent_id, username)
);
CREATE INDEX IF NOT EXISTS idx_edu_parent_account_binding_username
    ON public.edu_parent_account_binding (username, status);

CREATE TABLE IF NOT EXISTS public.edu_home_notice (
    id varchar(64) PRIMARY KEY,
    school_id varchar(64),
    class_id varchar(64) NOT NULL,
    title varchar(200) NOT NULL,
    content text NOT NULL,
    receipt_required boolean NOT NULL DEFAULT false,
    publish_at timestamp,
    expire_at timestamp,
    status varchar(24) NOT NULL DEFAULT 'DRAFT',
    publisher_username varchar(100),
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp
);
CREATE INDEX IF NOT EXISTS idx_edu_home_notice_class ON public.edu_home_notice (class_id, status);

CREATE TABLE IF NOT EXISTS public.edu_home_notice_target (
    id varchar(64) PRIMARY KEY,
    notice_id varchar(64) NOT NULL,
    student_id varchar(64) NOT NULL,
    parent_id varchar(64) NOT NULL,
    delivery_status varchar(24) NOT NULL DEFAULT 'DELIVERED',
    read_at timestamp,
    receipt_status varchar(24) NOT NULL DEFAULT 'PENDING',
    receipt_at timestamp,
    receipt_comment text,
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_home_notice_target UNIQUE (notice_id, student_id, parent_id)
);
CREATE INDEX IF NOT EXISTS idx_edu_home_notice_target_parent
    ON public.edu_home_notice_target (parent_id, notice_id);

-- Permission rows are intentionally added only when the corresponding menu
-- already exists; this keeps fresh and historical databases compatible.
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
               '家校中心第一交付切片权限'
        FROM (VALUES
            ('查看家长账号','education:home-school:parent:view','VIEW'),
            ('绑定家长账号','education:home-school:parent:create','CREATE'),
            ('失效家长账号','education:home-school:parent:update','UPDATE'),
            ('查看家校通知','education:home-school:notice:view','VIEW'),
            ('创建家校通知','education:home-school:notice:create','CREATE'),
            ('发布家校通知','education:home-school:notice:update','UPDATE')
        ) v(label, code, action)
        WHERE NOT EXISTS (SELECT 1 FROM public.t_permission p WHERE p.permission_code = v.code);
    END IF;
END $$;
