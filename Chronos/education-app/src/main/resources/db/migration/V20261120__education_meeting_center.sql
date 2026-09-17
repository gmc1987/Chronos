CREATE TABLE IF NOT EXISTS edu_meeting_room (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  room_code varchar(64) NOT NULL,
  room_name varchar(128) NOT NULL,
  campus_id varchar(64),
  building_name varchar(128),
  location varchar(255),
  capacity integer NOT NULL,
  equipment_json text,
  approval_mode varchar(16) NOT NULL DEFAULT 'AUTO',
  approver_username varchar(128),
  enabled boolean NOT NULL DEFAULT true,
  record_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uk_edu_meeting_room_code UNIQUE (room_code),
  CONSTRAINT ck_edu_meeting_room_capacity CHECK (capacity > 0),
  CONSTRAINT ck_edu_meeting_room_approval CHECK (
    approval_mode IN ('AUTO', 'MANUAL')
    AND (approval_mode = 'AUTO' OR approver_username IS NOT NULL)
  )
);

CREATE TABLE IF NOT EXISTS edu_meeting (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  title varchar(200) NOT NULL,
  agenda text,
  meeting_type varchar(16) NOT NULL,
  start_time timestamp NOT NULL,
  end_time timestamp NOT NULL,
  organizer_username varchar(128) NOT NULL,
  room_id varchar(64),
  meeting_provider varchar(32),
  external_meeting_id varchar(128),
  join_url varchar(1000),
  online_access_code varchar(128),
  status varchar(24) NOT NULL DEFAULT 'DRAFT',
  room_decision_by varchar(128),
  room_decision_at timestamp,
  room_decision_comment varchar(1000),
  published_at timestamp,
  cancelled_at timestamp,
  cancel_reason varchar(1000),
  record_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT fk_edu_meeting_room FOREIGN KEY (room_id)
    REFERENCES edu_meeting_room(id),
  CONSTRAINT ck_edu_meeting_period CHECK (end_time > start_time),
  CONSTRAINT ck_edu_meeting_type CHECK (
    meeting_type IN ('ONSITE', 'ONLINE', 'HYBRID')
  ),
  CONSTRAINT ck_edu_meeting_status CHECK (
    status IN ('DRAFT', 'PENDING_ROOM', 'REJECTED', 'PUBLISHED', 'CANCELLED', 'COMPLETED')
  )
);

CREATE INDEX IF NOT EXISTS idx_edu_meeting_room_period
  ON edu_meeting(room_id, start_time, end_time, status);
CREATE INDEX IF NOT EXISTS idx_edu_meeting_organizer
  ON edu_meeting(organizer_username, start_time DESC);

CREATE TABLE IF NOT EXISTS edu_meeting_participant (
  id varchar(64) PRIMARY KEY,
  create_by varchar(128) NOT NULL,
  create_time timestamp NOT NULL,
  last_update_by varchar(128),
  last_update_time timestamp,
  meeting_id varchar(64) NOT NULL,
  username varchar(128) NOT NULL,
  participant_role varchar(24) NOT NULL DEFAULT 'ATTENDEE',
  response_status varchar(24) NOT NULL DEFAULT 'INVITED',
  response_comment varchar(500),
  responded_at timestamp,
  CONSTRAINT fk_edu_meeting_participant_meeting FOREIGN KEY (meeting_id)
    REFERENCES edu_meeting(id) ON DELETE CASCADE,
  CONSTRAINT uk_edu_meeting_participant UNIQUE (meeting_id, username),
  CONSTRAINT ck_edu_meeting_participant_response CHECK (
    response_status IN ('INVITED', 'ACCEPTED', 'DECLINED', 'LEAVE')
  )
);

CREATE INDEX IF NOT EXISTS idx_edu_meeting_participant_user
  ON edu_meeting_participant(username, meeting_id);

-- 原行业模板已预留会议菜单与基础权限，此处补齐可访问路由和审批原子权限。
UPDATE t_menu
SET path = '/admin/education/meeting/manage',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE id = 'd1f6672d-3920-462d-bbce-a69bbc106385';

UPDATE t_menu
SET path = '/admin/education/meeting/rooms',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE id = '41594ad4-2ce5-4279-8a3a-709979c58246';

INSERT INTO t_permission (
  id, create_by, create_time, permission_code, permission_name,
  permission_type, action_type, built_in, status, menu_id,
  resource_type, scope_type, description
)
VALUES (
  gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
  'education:meeting:approve', '审批会议室预约',
  'MENU_ACTION', 'APPROVE', true, 1,
  'd1f6672d-3920-462d-bbce-a69bbc106385',
  'MENU', 'ROLE', '处理需要人工审批的会议室预约'
)
ON CONFLICT (permission_code) DO UPDATE SET
  menu_id = EXCLUDED.menu_id,
  permission_name = EXCLUDED.permission_name,
  action_type = EXCLUDED.action_type,
  status = 1;

-- 会议中心属于校级通用能力：教育管理员可管理，平台管理员保留救援权限。
INSERT INTO t_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('ROLE_PLATFORM_ADMIN', 'EDU_ADMIN', 'SUPER_ADMIN')
  AND permission.permission_code LIKE 'education:meeting:%'
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu (role_id, menu_id)
SELECT role.id, menu.id
FROM t_role role
CROSS JOIN t_menu menu
WHERE role.role_code IN ('ROLE_PLATFORM_ADMIN', 'EDU_ADMIN', 'SUPER_ADMIN')
  AND menu.id IN (
    '566fd6ee-5099-449a-899d-c29ab08edcc8',
    'd1f6672d-3920-462d-bbce-a69bbc106385',
    '41594ad4-2ce5-4279-8a3a-709979c58246'
  )
ON CONFLICT DO NOTHING;

INSERT INTO t_role_menu_permission (role_id, menu_id, permission_id)
SELECT role.id, permission.menu_id, permission.id
FROM t_role role
CROSS JOIN t_permission permission
WHERE role.role_code IN ('ROLE_PLATFORM_ADMIN', 'EDU_ADMIN', 'SUPER_ADMIN')
  AND permission.permission_code LIKE 'education:meeting:%'
  AND permission.menu_id IS NOT NULL
ON CONFLICT DO NOTHING;

INSERT INTO t_portal_application (
  id, create_by, create_time, app_code, app_name, description,
  enabled, icon, open_mode, recommended, route_path, sort_order
)
VALUES (
  gen_random_uuid()::text, 'SYSTEM', CURRENT_TIMESTAMP,
  'EDUCATION_MEETINGS', '我的会议', '查看会议邀请、反馈参会状态并进入线上会议',
  true, 'Calendar', 'INTERNAL', true,
  '/portal/education/meetings', 45
)
ON CONFLICT (app_code) DO UPDATE SET
  app_name = EXCLUDED.app_name,
  description = EXCLUDED.description,
  enabled = true,
  route_path = EXCLUDED.route_path,
  last_update_by = 'SYSTEM',
  last_update_time = CURRENT_TIMESTAMP;
