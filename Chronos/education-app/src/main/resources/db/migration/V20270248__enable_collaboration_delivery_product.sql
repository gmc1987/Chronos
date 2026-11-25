UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'COLLABORATION_OFFICE' AND updated_by IS NULL;
