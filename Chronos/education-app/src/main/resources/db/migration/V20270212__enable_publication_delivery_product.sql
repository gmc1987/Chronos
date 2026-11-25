UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'CAMPUS_PUBLICATIONS' AND updated_by IS NULL;
