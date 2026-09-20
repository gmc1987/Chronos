-- Complete the grade-center navigation metadata without rewriting any
-- previously executed reference or grade-center migrations.
BEGIN;

UPDATE t_menu
SET path = '/admin/education/score-center',
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE id = 'd42db680-5cac-45c1-90d3-f9335902f93f'
  AND menu_name = '成绩中心';

UPDATE t_menu
SET path = CASE id
    WHEN '827b2fef-b55f-49be-a226-3c9b5dcc71df' THEN '/admin/education/score-center'
    WHEN '2305517c-017f-440b-9197-6d49cb8e8383' THEN '/admin/education/score-center/class-analysis'
    WHEN '3223070b-62d8-442e-a242-e4ae808ede55' THEN '/admin/education/score-center/grade-analysis'
    WHEN '0b800ff7-74a4-4f41-a567-6c8b78876303' THEN '/admin/education/score-center/subject-analysis'
    WHEN '6e6048f0-32e0-4720-912b-020217d80f74' THEN '/admin/education/score-center/trend-analysis'
    WHEN 'a8033486-381b-4f9e-a42e-0e652d530237' THEN '/admin/education/score-center/knowledge-analysis'
    ELSE path
  END,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE parent_id = 'd42db680-5cac-45c1-90d3-f9335902f93f'
  AND id IN (
    '827b2fef-b55f-49be-a226-3c9b5dcc71df',
    '2305517c-017f-440b-9197-6d49cb8e8383',
    '3223070b-62d8-442e-a242-e4ae808ede55',
    '0b800ff7-74a4-4f41-a567-6c8b78876303',
    '6e6048f0-32e0-4720-912b-020217d80f74',
    'a8033486-381b-4f9e-a42e-0e652d530237'
  );

UPDATE t_permission
SET menu_id = CASE
    WHEN permission_code LIKE 'education:score:scheme:%'
      OR permission_code LIKE 'education:score:gradebook:%'
      OR permission_code IN ('education:score:review', 'education:score:privacy:view')
      THEN '827b2fef-b55f-49be-a226-3c9b5dcc71df'
    WHEN permission_code LIKE 'education:score:class-analysis:%'
      THEN '2305517c-017f-440b-9197-6d49cb8e8383'
    WHEN permission_code LIKE 'education:score:grade-analysis:%'
      THEN '3223070b-62d8-442e-a242-e4ae808ede55'
    WHEN permission_code LIKE 'education:score:subject-analysis:%'
      THEN '0b800ff7-74a4-4f41-a567-6c8b78876303'
    WHEN permission_code LIKE 'education:score:trend-analysis:%'
      THEN '6e6048f0-32e0-4720-912b-020217d80f74'
    WHEN permission_code LIKE 'education:score:knowledge-analysis:%'
      THEN 'a8033486-381b-4f9e-a42e-0e652d530237'
    ELSE menu_id
  END,
    last_update_by = 'SYSTEM',
    last_update_time = CURRENT_TIMESTAMP
WHERE permission_code LIKE 'education:score:%';

COMMIT;
