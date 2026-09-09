package com.chronos.knowledge.service;

import com.chronos.portal.spi.PortalContribution;
import com.chronos.portal.spi.PortalContributionProvider;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 在统一门户暴露教育知识问答入口，不在首页直接调用外部模型。 */
@Component
public class KnowledgePortalContributionProvider implements PortalContributionProvider {
	@Override
	public String providerCode() {
		return "AI";
	}

	@Override
	public PortalContribution load(String username) {
		return new PortalContribution(
				providerCode(),
				true,
				"ok",
				Map.of(
						"title", "校园知识助手",
						"description", "基于学校制度和教务知识库回答问题，并附引用来源。",
						"examples", List.of("学生请假需要哪些材料？", "教师调课需要经过谁审批？"),
						"allRoute", "/admin/education/knowledge"));
	}
}
