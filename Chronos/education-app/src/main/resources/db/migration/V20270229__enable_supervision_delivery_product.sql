UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'EDU_SUPERVISION' AND updated_by IS NULL;
