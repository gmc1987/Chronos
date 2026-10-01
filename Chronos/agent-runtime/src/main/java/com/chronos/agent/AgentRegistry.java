package com.chronos.agent;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Immutable registry of server-provided skills and tools. Invalid registrations fail startup. */
@Component
public final class AgentRegistry {
	private final Map<String, AgentSkill> skills;
	private final Map<String, RegisteredTool> tools;

	public AgentRegistry(Collection<AgentSkill> skills, Collection<AgentTool<?, ?>> tools) {
		Map<String, AgentSkill> skillMap = new HashMap<>();
		Map<String, RegisteredTool> toolMap = new HashMap<>();
		for (AgentTool<?, ?> tool : tools) {
			Objects.requireNonNull(tool, "tool不能为空");
			String code = requireCode(tool.code());
			int version = tool.inputSchemaVersion();
			ToolRiskLevel risk = tool.riskLevel();
			if (version < 1 || risk == null) {
				throw new IllegalArgumentException("无效的工具配置：" + code);
			}
			if (toolMap.putIfAbsent(code, new RegisteredTool(version, risk, tool)) != null) {
				throw new IllegalArgumentException("重复的工具代码：" + code);
			}
		}
		for (AgentSkill skill : skills) {
			Objects.requireNonNull(skill, "skill不能为空");
			String code = requireCode(skill.code());
			if (skill.schemaVersion() < 1 || skill.maxToolCalls() < 0) {
				throw new IllegalArgumentException("无效的技能配置：" + code);
			}
			Set<String> allowed = Objects.requireNonNull(skill.allowedToolCodes(), "allowedToolCodes不能为空");
			for (String toolCode : allowed) {
				if (!toolMap.containsKey(requireCode(toolCode))) {
					throw new IllegalArgumentException("技能引用了未注册的工具：" + code + " / " + toolCode);
				}
			}
			if (skillMap.putIfAbsent(code, new RegisteredSkill(code, skill.schemaVersion(),
					Set.copyOf(allowed), skill.maxToolCalls())) != null) {
				throw new IllegalArgumentException("重复的技能代码：" + code);
			}
		}
		this.skills = Map.copyOf(skillMap);
		this.tools = Map.copyOf(toolMap);
	}

	public AgentSkill skill(String code) {
		return code == null ? null : skills.get(code);
	}

	public AgentTool<?, ?> tool(String code) {
		RegisteredTool registration = registration(code);
		return registration == null ? null : registration.delegate();
	}

	RegisteredTool registration(String code) {
		return code == null ? null : tools.get(code);
	}

	private static String requireCode(String code) {
		if (code == null || code.isBlank() || !code.equals(code.trim())) {
			throw new IllegalArgumentException("技能/工具代码不能为空且不能包含首尾空白");
		}
		return code;
	}

	private record RegisteredSkill(String code, int schemaVersion,
			Set<String> allowedToolCodes, int maxToolCalls) implements AgentSkill {
	}

	record RegisteredTool(int inputSchemaVersion, ToolRiskLevel riskLevel, AgentTool<?, ?> delegate) {
	}
}
