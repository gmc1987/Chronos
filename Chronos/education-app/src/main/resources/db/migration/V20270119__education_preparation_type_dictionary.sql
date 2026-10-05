-- 集体备课创建表单必须有与服务端提交规则一致的备课类型。
INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-root:EDU_PREPARATION_TYPE'), 'system', current_timestamp,
       'EDU_PREPARATION_TYPE', '备课类型', NULL, NULL, 1
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict
    WHERE dict_code = 'EDU_PREPARATION_TYPE' AND parent_id IS NULL
);

WITH items(dict_value, dict_name) AS (
    VALUES ('INDIVIDUAL', '个人备课'), ('COLLECTIVE', '集体备课')
)
INSERT INTO t_dict(id, create_by, create_time, dict_code, dict_name,
                   dict_value, parent_id, status)
SELECT md5('chronos-dict-item:EDU_PREPARATION_TYPE:' || items.dict_value),
       'system', current_timestamp, 'EDU_PREPARATION_TYPE', items.dict_name,
       items.dict_value, root.id, 1
FROM items
CROSS JOIN LATERAL (
    SELECT id FROM t_dict
    WHERE dict_code = 'EDU_PREPARATION_TYPE' AND parent_id IS NULL
    ORDER BY create_time, id LIMIT 1
) root
WHERE NOT EXISTS (
    SELECT 1 FROM t_dict existing
    WHERE existing.dict_code = 'EDU_PREPARATION_TYPE'
      AND existing.dict_value = items.dict_value
);
