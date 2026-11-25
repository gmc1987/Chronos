UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'AI_KNOWLEDGE' AND updated_by IS NULL;
