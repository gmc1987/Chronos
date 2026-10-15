ALTER TABLE edu_scheduling_agent_proposal
    ADD COLUMN IF NOT EXISTS rule_catalog_version varchar(48),
    ADD COLUMN IF NOT EXISTS validator_version varchar(48);

-- 历史确认已直接写正式约束；只标为旧版，不能伪称通过新版核验。
UPDATE edu_scheduling_agent_proposal
SET rule_catalog_version = 'LEGACY_UNVERSIONED',
    validator_version = 'LEGACY_UNVERSIONED'
WHERE rule_catalog_version IS NULL;
