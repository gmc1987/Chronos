UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'EDU_OPERATIONS_AGENT' AND updated_by IS NULL;
