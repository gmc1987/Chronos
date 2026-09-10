package com.chronos.workflow;

import java.util.Map;

/**
 * 行业模块在流程实例创建前执行的业务校验扩展点。
 *
 * <p>工作流模块只负责通用状态机，课程、课表等领域资源的权限必须由所属行业模块校验，
 * 避免通用引擎反向依赖具体业务实体。</p>
 */
public interface WorkflowStartValidator {
	boolean supports(String flowCode);

	void validate(String actor, Map<String, Object> formData);
}
