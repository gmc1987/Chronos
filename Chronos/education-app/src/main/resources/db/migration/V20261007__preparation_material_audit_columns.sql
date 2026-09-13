-- PreparationMaterial has unannotated audit/time fields; add the exact
-- camel-case names expected with PhysicalNamingStrategyStandardImpl.
ALTER TABLE edu_preparation_material
    ADD COLUMN IF NOT EXISTS "boundAt" timestamptz;

ALTER TABLE edu_preparation_material
    ADD COLUMN IF NOT EXISTS "createBy" varchar(128);

ALTER TABLE edu_preparation_material
    ADD COLUMN IF NOT EXISTS "createTime" timestamptz;
