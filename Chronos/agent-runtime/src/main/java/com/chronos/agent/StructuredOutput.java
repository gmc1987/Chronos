package com.chronos.agent;

/**
 * Validated structured model output. The runtime stores only the validated
 * payload and never treats arbitrary model text as a domain command.
 */
public record StructuredOutput(
		String schemaId,
		int schemaVersion,
		String payloadJson) {
	public StructuredOutput {
		if (schemaId == null || schemaId.isBlank()) {
			throw new IllegalArgumentException("schemaId不能为空");
		}
		if (payloadJson == null || payloadJson.isBlank()) {
			throw new IllegalArgumentException("结构化输出不能为空");
		}
	}
}
