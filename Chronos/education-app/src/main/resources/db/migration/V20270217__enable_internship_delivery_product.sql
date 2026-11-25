UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'EDU_INTERNSHIP' AND updated_by IS NULL;
