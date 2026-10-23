ALTER TABLE edu_operation_agent_condition
	ADD COLUMN IF NOT EXISTS parsed_condition_json text NOT NULL DEFAULT '{}';

COMMENT ON COLUMN edu_operation_agent_condition.parsed_condition_json IS
	'由服务端白名单解析器生成的可执行条件；原文始终保存在 condition_text';
