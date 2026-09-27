CREATE TABLE IF NOT EXISTS edu_parent_meeting_scope (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  meeting_id varchar(64) NOT NULL,
  scope_type varchar(16) NOT NULL,
  scope_id varchar(64) NOT NULL,
  CONSTRAINT uk_edu_parent_meeting_scope UNIQUE (meeting_id, scope_type, scope_id),
  CONSTRAINT ck_edu_parent_meeting_scope_type CHECK (scope_type IN ('CLASS', 'GRADE', 'STUDENT'))
);
CREATE INDEX IF NOT EXISTS idx_edu_parent_meeting_scope_meeting
  ON edu_parent_meeting_scope (meeting_id);

DO $$
DECLARE menu_id varchar(64);
BEGIN
  SELECT id INTO menu_id FROM public.t_menu WHERE menu_name IN ('家校中心', '家长会管理') LIMIT 1;
  IF menu_id IS NOT NULL THEN
    INSERT INTO public.t_permission
      (id, create_by, create_time, permission_name, permission_code, permission_type,
       menu_id, action_type, resource_type, scope_type, status, built_in, description)
    VALUES
      (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, '创建家长会',
       'education:parent-meeting:create', 'MENU_ACTION', menu_id, 'CREATE', 'MENU',
       'ROLE', 1, true, '按班级、年级或学生范围创建家长会'),
      (gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP, '管理家长会',
       'education:parent-meeting:manage', 'MENU_ACTION', menu_id, 'MANAGE', 'MENU',
       '管理家长会编排与执行闭环')
    ON CONFLICT (permission_code) DO NOTHING;
  END IF;
END $$;
