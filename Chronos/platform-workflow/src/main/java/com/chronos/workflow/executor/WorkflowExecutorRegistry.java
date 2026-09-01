package com.chronos.workflow.executor;

import java.util.List;
import com.chronos.model.workflow.WorkflowNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

@Component
public class WorkflowExecutorRegistry {
	private final ObjectMapper json=new ObjectMapper();
	public List<ExecutorDescriptor> descriptors() {
		return List.of(
				new ExecutorDescriptor("spring-service", "内部变量服务", "SERVICE_TASK", "按节点扩展配置中的 outputKey/value 写入流程变量", true),
				new ExecutorDescriptor("http-api", "HTTP API", "HTTP_TASK", "调用集成中心管理的 HTTP API", false),
				new ExecutorDescriptor("ai-agent", "AI Agent", "AGENT_TASK", "调用 Agent Runtime 中已发布的 Agent", false),
				new ExecutorDescriptor("message", "流程消息", "MESSAGE_TASK", "记录消息主题和内容到流程变量，供消息中心消费", true));
	}
	public String execute(WorkflowNode node,String variables){try{ObjectNode result=(ObjectNode)json.readTree(variables==null||variables.isBlank()?"{}":variables);var config=json.readTree(node.getPropertiesJson()==null?"{}":node.getPropertiesJson());String executor=node.getExecutor();if("spring-service".equals(executor)){String key=config.path("outputKey").asText();if(key.isBlank())throw new IllegalArgumentException("内部服务节点必须配置 outputKey");result.set(key,config.path("value"));}else if("message".equals(executor)){ObjectNode message=json.createObjectNode();message.put("subject",config.path("subject").asText(node.getNodeName()));message.put("content",config.path("content").asText());result.set("lastMessage",message);}else throw new IllegalArgumentException("执行器未启用："+executor);return json.writeValueAsString(result);}catch(IllegalArgumentException e){throw e;}catch(Exception e){throw new IllegalArgumentException("自动节点执行失败："+e.getMessage(),e);}}
}
