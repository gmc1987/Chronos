package com.chronos.model.vo;

import java.util.Map;
import java.util.Set;

/** 平台级数据范围；行业模块读取 scopeTypes，并解释自身的领域范围。 */
public record DataScopeContext(
		boolean fullAccess,
		String employeeId,
		Set<String> organizationIds,
		Set<String> organizationUnitIds,
		Set<String> employeeIds,
		Set<String> scopeTypes,
		Map<String, Set<String>> resourceIds) {
}
