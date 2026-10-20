-- 微信小程序学校入口、短期绑定会话及家长邀请闭环。
-- OpenID 只作为外部登录凭据；正式用户、教育档案和监护关系继续使用现有主表。

CREATE TABLE IF NOT EXISTS public.edu_mini_program_school_entry (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    organization_id varchar(64) NOT NULL,
    scene_code varchar(32) NOT NULL,
    page_path varchar(200) NOT NULL DEFAULT 'pages/bind/index',
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    generated_at timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at timestamp,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_edu_mini_program_school_org UNIQUE (organization_id),
    CONSTRAINT uk_edu_mini_program_school_scene UNIQUE (scene_code),
    CONSTRAINT ck_edu_mini_program_school_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_edu_mini_program_school_org FOREIGN KEY (organization_id)
        REFERENCES public.t_organization(id)
);

CREATE TABLE IF NOT EXISTS public.edu_mini_program_binding_session (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    token_hash varchar(64) NOT NULL,
    identity_source varchar(64) NOT NULL,
    open_id varchar(128) NOT NULL,
    union_id varchar(128),
    organization_id varchar(64),
    guardian_invitation_id varchar(64),
    expires_at timestamp NOT NULL,
    consumed_at timestamp,
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_edu_mini_program_session_token UNIQUE (token_hash),
    CONSTRAINT ck_edu_mini_program_session_status
        CHECK (status IN ('PENDING', 'CONSUMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT fk_edu_mini_program_session_org FOREIGN KEY (organization_id)
        REFERENCES public.t_organization(id)
);

CREATE INDEX IF NOT EXISTS idx_edu_mini_program_session_expiry
    ON public.edu_mini_program_binding_session(status, expires_at);
CREATE INDEX IF NOT EXISTS idx_edu_mini_program_session_identity
    ON public.edu_mini_program_binding_session(identity_source, open_id);

CREATE TABLE IF NOT EXISTS public.edu_guardian_binding_invitation (
    id varchar(64) PRIMARY KEY,
    create_by varchar(128) NOT NULL,
    create_time timestamp NOT NULL,
    last_update_by varchar(128),
    last_update_time timestamp,
    scene_code varchar(32) NOT NULL,
    organization_id varchar(64) NOT NULL,
    student_id varchar(64) NOT NULL,
    relationship varchar(32) NOT NULL,
    primary_guardian boolean NOT NULL DEFAULT false,
    emergency_contact boolean NOT NULL DEFAULT false,
    issued_by varchar(100) NOT NULL,
    expires_at timestamp NOT NULL,
    consumed_at timestamp,
    consumed_by varchar(100),
    status varchar(24) NOT NULL DEFAULT 'PENDING',
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_edu_guardian_binding_scene UNIQUE (scene_code),
    CONSTRAINT ck_edu_guardian_binding_status
        CHECK (status IN ('PENDING', 'CONSUMED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT fk_edu_guardian_binding_org FOREIGN KEY (organization_id)
        REFERENCES public.t_organization(id),
    CONSTRAINT fk_edu_guardian_binding_student FOREIGN KEY (student_id)
        REFERENCES public.edu_student_profile(id)
);

CREATE INDEX IF NOT EXISTS idx_edu_guardian_binding_student
    ON public.edu_guardian_binding_invitation(student_id, status, expires_at);

ALTER TABLE public.edu_mini_program_binding_session
    ADD CONSTRAINT fk_edu_mini_program_session_invitation
    FOREIGN KEY (guardian_invitation_id)
    REFERENCES public.edu_guardian_binding_invitation(id);

-- 历史数据若存在同一身份源下同一账号的多条 ACTIVE 记录，仅保留最早的有效绑定。
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY source_id, user_id
               ORDER BY linked_at NULLS LAST, create_time, id
           ) AS position
    FROM public.iam_external_identity
    WHERE status = 'ACTIVE'
)
UPDATE public.iam_external_identity identity
SET status = 'DISABLED',
    last_update_by = 'wechat_identity_constraint',
    last_update_time = CURRENT_TIMESTAMP
FROM ranked
WHERE identity.id = ranked.id
  AND ranked.position > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_iam_external_identity_source_user_active
    ON public.iam_external_identity(source_id, user_id)
    WHERE status = 'ACTIVE';

INSERT INTO public.t_role (
    id, create_by, create_time, built_in, role_code, role_name, status, description
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, true,
       role_code, role_name, 1, description
FROM (VALUES
    ('EDU_PARENT', '家长', '家长门户及学生关系范围内的移动端访问'),
    ('EDU_STUDENT', '学生', '学生门户及本人教育数据访问')
) source(role_code, role_name, description)
WHERE NOT EXISTS (
    SELECT 1 FROM public.t_role existing WHERE existing.role_code = source.role_code
);

INSERT INTO public.t_permission (
    id, create_by, create_time, built_in, permission_code, permission_name,
    permission_type, resource_type, action_type, menu_id, status
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, true,
       'education:mini-program:entry:manage', '管理学校微信小程序入口',
       'MENU_ACTION', NULL, 'MANAGE', NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM public.t_permission
    WHERE permission_code = 'education:mini-program:entry:manage'
);

INSERT INTO public.t_role_permission(role_id, permission_id)
SELECT role.id, permission.id
FROM public.t_role role
CROSS JOIN public.t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'EDU_ADMIN')
  AND permission.permission_code = 'education:mini-program:entry:manage'
  AND NOT EXISTS (
      SELECT 1 FROM public.t_role_permission existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

INSERT INTO public.t_menu (
    id, create_by, create_time, last_update_by, last_update_time,
    menu_name, order_num, parent_id, path
)
SELECT gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, 'SYSTEM', CURRENT_TIMESTAMP,
       '微信小程序入口', 7, parent.id, '/admin/education/mini-program'
FROM public.t_menu parent
WHERE parent.menu_name = '家校中心'
  AND NOT EXISTS (
      SELECT 1 FROM public.t_menu existing
      WHERE existing.path = '/admin/education/mini-program'
  )
LIMIT 1;

INSERT INTO public.t_role_menu(role_id, menu_id)
SELECT role.id, menu.id
FROM public.t_role role
CROSS JOIN public.t_menu menu
WHERE role.role_code IN ('SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'EDU_ADMIN')
  AND menu.path = '/admin/education/mini-program'
ON CONFLICT DO NOTHING;

INSERT INTO public.t_role_menu_permission(role_id, menu_id, permission_id)
SELECT role.id, menu.id, permission.id
FROM public.t_role role
CROSS JOIN public.t_menu menu
CROSS JOIN public.t_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'ROLE_PLATFORM_ADMIN', 'EDU_ADMIN')
  AND menu.path = '/admin/education/mini-program'
  AND permission.permission_code = 'education:mini-program:entry:manage'
ON CONFLICT DO NOTHING;
