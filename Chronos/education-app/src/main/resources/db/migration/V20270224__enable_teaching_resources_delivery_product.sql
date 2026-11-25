UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'EDU_TEACHING_RESOURCES' AND updated_by IS NULL;
