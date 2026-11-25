UPDATE cd_product_catalog
SET enabled = true
WHERE module_code = 'THIRD_PARTY_INTEGRATIONS' AND updated_by IS NULL;
