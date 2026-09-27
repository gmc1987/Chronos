-- 班级群管理闭环：群、成员快照和变更审计。不得修改既有家校迁移。
CREATE TABLE IF NOT EXISTS public.edu_class_group (
    id varchar(64) PRIMARY KEY,
    school_id varchar(64) NOT NULL,
    class_id varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    last_sync_key varchar(128),
    row_version bigint NOT NULL DEFAULT 0,
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_class_group_class UNIQUE (class_id)
);
CREATE INDEX IF NOT EXISTS idx_edu_class_group_school_status
    ON public.edu_class_group (school_id, status, create_time);

CREATE TABLE IF NOT EXISTS public.edu_class_group_member (
    id varchar(64) PRIMARY KEY,
    group_id varchar(64) NOT NULL,
    member_type varchar(24) NOT NULL,
    member_id varchar(64) NOT NULL,
    username varchar(128),
    role varchar(24) NOT NULL DEFAULT 'MEMBER',
    source varchar(24) NOT NULL DEFAULT 'DERIVED',
    status varchar(24) NOT NULL DEFAULT 'ACTIVE',
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp,
    CONSTRAINT uk_edu_class_group_member UNIQUE (group_id, member_type, member_id)
);
CREATE INDEX IF NOT EXISTS idx_edu_class_group_member_active
    ON public.edu_class_group_member (group_id, status, member_type);

CREATE TABLE IF NOT EXISTS public.edu_class_group_audit (
    id varchar(64) PRIMARY KEY,
    group_id varchar(64) NOT NULL,
    action varchar(48) NOT NULL,
    member_type varchar(24),
    member_id varchar(64),
    detail text,
    actor varchar(128) NOT NULL,
    create_by varchar(128) NOT NULL DEFAULT 'SYSTEM',
    create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_update_by varchar(128),
    last_update_time timestamp
);
CREATE INDEX IF NOT EXISTS idx_edu_class_group_audit_group
    ON public.edu_class_group_audit (group_id, create_time);

UPDATE public.t_menu
SET path = '/admin/education/home-school/class-groups'
WHERE menu_name = '班级群管理'
  AND (path IS NULL OR path = '');
