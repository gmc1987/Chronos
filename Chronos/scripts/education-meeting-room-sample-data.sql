-- 会议中心可重复执行的基础场地数据。
-- 校区使用现有组织编码解析，审批人使用现有有效账号，不构造伪外键。

BEGIN;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM t_organization
    WHERE org_code = 'EDU-NCVC-MAIN' AND status = 1
  ) THEN
    RAISE EXCEPTION '缺少启用的主校区 EDU-NCVC-MAIN';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM t_organization
    WHERE org_code = 'EDU-NCVC-TRAIN' AND status = 1
  ) THEN
    RAISE EXCEPTION '缺少启用的实训校区 EDU-NCVC-TRAIN';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM t_admin_user
    WHERE username = 'academic.demo'
      AND status = 1
      AND COALESCE(account_locked, false) = false
  ) THEN
    RAISE EXCEPTION '缺少可用的会议室审批人 academic.demo';
  END IF;
END $$;

WITH room_seed (
  room_code,
  room_name,
  campus_code,
  building_name,
  location,
  capacity,
  equipment_json,
  approval_mode,
  approver_username,
  enabled
) AS (
  VALUES
    ('MAIN-ADM-101', '行政楼第一会议室', 'EDU-NCVC-MAIN', '行政楼', '1楼101室', 12,
      '["液晶屏","白板","会议电话"]', 'AUTO', NULL, true),
    ('MAIN-ADM-201', '行政楼校务会议室', 'EDU-NCVC-MAIN', '行政楼', '2楼201室', 30,
      '["激光投影","无线投屏","视频会议终端","录播"]', 'MANUAL', 'academic.demo', true),
    ('MAIN-REPORT-01', '主校区学术报告厅', 'EDU-NCVC-MAIN', '图书综合楼', '1楼报告厅', 180,
      '["LED大屏","舞台灯光","无线麦克风","录播","视频会议终端"]', 'MANUAL', 'academic.demo', true),
    ('MAIN-TEACH-305', '教研集备室', 'EDU-NCVC-MAIN', '教学楼A座', '3楼305室', 20,
      '["智慧黑板","无线投屏","白板"]', 'AUTO', NULL, true),
    ('MAIN-CAREER-208', '招生就业洽谈室', 'EDU-NCVC-MAIN', '学生服务中心', '2楼208室', 16,
      '["液晶屏","摄像头","会议麦克风"]', 'MANUAL', 'academic.demo', true),
    ('TRAIN-A205', '实训校区会议室', 'EDU-NCVC-TRAIN', '实训楼A座', '2楼205室', 24,
      '["投影仪","白板","会议电话"]', 'AUTO', NULL, true),
    ('TRAIN-INDUSTRY-01', '产教融合厅', 'EDU-NCVC-TRAIN', '产教融合中心', '1楼多功能厅', 80,
      '["LED大屏","无线麦克风","远程会议","录播"]', 'MANUAL', 'academic.demo', true),
    ('TRAIN-VIDEO-302', '远程视频会议室', 'EDU-NCVC-TRAIN', '实训楼B座', '3楼302室', 10,
      '["视频会议终端","双屏显示","阵列麦克风"]', 'AUTO', NULL, true)
)
INSERT INTO edu_meeting_room (
  id,
  create_by,
  create_time,
  last_update_by,
  last_update_time,
  room_code,
  room_name,
  campus_id,
  building_name,
  location,
  capacity,
  equipment_json,
  approval_mode,
  approver_username,
  enabled,
  record_version
)
SELECT
  gen_random_uuid()::text,
  'SYSTEM',
  CURRENT_TIMESTAMP,
  'SYSTEM',
  CURRENT_TIMESTAMP,
  seed.room_code,
  seed.room_name,
  campus.id,
  seed.building_name,
  seed.location,
  seed.capacity,
  seed.equipment_json,
  seed.approval_mode,
  seed.approver_username,
  seed.enabled,
  0
FROM room_seed seed
JOIN t_organization campus
  ON campus.org_code = seed.campus_code
 AND campus.status = 1
ON CONFLICT (room_code) DO UPDATE SET
  room_name = EXCLUDED.room_name,
  campus_id = EXCLUDED.campus_id,
  building_name = EXCLUDED.building_name,
  location = EXCLUDED.location,
  capacity = EXCLUDED.capacity,
  equipment_json = EXCLUDED.equipment_json,
  approval_mode = EXCLUDED.approval_mode,
  approver_username = EXCLUDED.approver_username,
  enabled = EXCLUDED.enabled,
  last_update_by = 'SYSTEM',
  last_update_time = CURRENT_TIMESTAMP,
  record_version = edu_meeting_room.record_version + 1;

COMMIT;
