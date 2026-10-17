-- 教师工作台会展示协同办公入口，流程 START ACL 必须与该角色保持一致。
-- 通用平台用户权限继续保留；这里补齐真实教师账号缺少的业务发起范围。
INSERT INTO wf_definition_acl (
    id, definition_id, subject_type, subject_id, action, enabled,
    create_by, create_time, last_update_by, last_update_time
)
SELECT
    gen_random_uuid()::text,
    definition.id,
    'ROLE',
    'EDU_TEACHER',
    'START',
    true,
    'SYSTEM',
    CURRENT_TIMESTAMP,
    'SYSTEM',
    CURRENT_TIMESTAMP
FROM wf_definition definition
WHERE definition.status = 'PUBLISHED'
  AND definition.flow_code IN (
      'STAFF_BUSINESS_TRIP_APPROVAL',
      'STAFF_VEHICLE_USE_APPROVAL',
      'STAFF_SEAL_USE_APPROVAL',
      'OFFICIAL_DOCUMENT_ISSUE_APPROVAL'
  )
ON CONFLICT (definition_id, subject_type, subject_id, action)
DO UPDATE SET
    enabled = true,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP;
