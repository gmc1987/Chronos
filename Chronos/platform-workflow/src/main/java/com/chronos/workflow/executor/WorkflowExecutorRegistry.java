package com.chronos.workflow.executor;

import com.chronos.model.workflow.WorkflowNode;
import com.chronos.workflow.WorkflowNotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 自动节点执行器注册表。外部调用默认关闭，必须由运维配置可信端点后启用。 */
@Component
public class WorkflowExecutorRegistry {
	private static final Pattern TEMPLATE_VARIABLE = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
	private final ObjectMapper json = new ObjectMapper();
	private final HttpClient httpClient;
	private final WorkflowNotificationService notifications;
	private final boolean httpEnabled;
	private final boolean agentEnabled;
	private final Set<String> allowedHosts;
	private final String agentBaseUrl;

	public WorkflowExecutorRegistry(
			WorkflowNotificationService notifications,
			@Value("${chronos.workflow.executors.http.enabled:false}") boolean httpEnabled,
			@Value("${chronos.workflow.executors.http.allowed-hosts:}") String allowedHosts,
			@Value("${chronos.workflow.executors.agent.enabled:false}") boolean agentEnabled,
			@Value("${chronos.workflow.executors.agent.base-url:}") String agentBaseUrl) {
		this.notifications = notifications;
		this.httpEnabled = httpEnabled;
		this.agentEnabled = agentEnabled;
		this.allowedHosts = parseHosts(allowedHosts);
		this.agentBaseUrl = agentBaseUrl == null ? "" : agentBaseUrl.strip();
		this.httpClient = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	public List<ExecutorDescriptor> descriptors() {
		return List.of(
				new ExecutorDescriptor("spring-service", "内部变量服务", "SERVICE_TASK",
						"按 outputKey/value 写入流程变量", true),
				new ExecutorDescriptor("http-api", "HTTP API", "HTTP_TASK",
						"调用运维白名单内的 HTTPS API", httpEnabled && !allowedHosts.isEmpty()),
				new ExecutorDescriptor("ai-agent", "AI Agent", "AGENT_TASK",
						"调用显式配置的 Agent Runtime", agentEnabled && !agentBaseUrl.isBlank()),
				new ExecutorDescriptor("message", "流程消息", "MESSAGE_TASK",
						"通过可靠 Outbox 发送站内流程通知", true));
	}

	/** 发布检查与运行时使用同一套执行器约束，避免流程发布后才发现配置不可执行。 */
	public String configurationError(WorkflowNode node) {
		try {
			JsonNode config = tree(node.getPropertiesJson(), "节点扩展配置");
			requireObjectOrBlank(node.getInputSchema(), "输入 Schema");
			requireObjectOrBlank(node.getOutputSchema(), "输出 Schema");
			requireObject(config.path("inputMapping"), "输入映射");
			requireObject(config.path("outputMapping"), "输出映射");
			requireObject(config.path("headers"), "请求头");
			validateHeaders(config.path("headers"));
			String executor = String.valueOf(node.getExecutor());
			String expectedNodeType = switch (executor) {
			case "spring-service" -> "SERVICE_TASK";
			case "message" -> "MESSAGE_TASK";
			case "http-api" -> "HTTP_TASK";
			case "ai-agent" -> "AGENT_TASK";
			default -> null;
			};
			if (expectedNodeType != null && !expectedNodeType.equals(node.getNodeType())) {
				return "执行器 " + executor + " 只能用于 " + expectedNodeType + " 节点";
			}
			if ("spring-service".equals(executor) && config.path("outputKey").asText().isBlank()) {
				return "内部变量服务必须配置输出变量 Key";
			}
			if ("message".equals(executor) && config.path("content").asText().isBlank()) {
				return "消息执行器必须配置消息内容";
			}
			if ("http-api".equals(executor)) {
				if (!httpEnabled || allowedHosts.isEmpty()) {
					return "HTTP 执行器尚未由运维启用或未配置主机白名单";
				}
				requireAllowedUri(config.path("url").asText());
			}
			if ("ai-agent".equals(executor)) {
				if (!agentEnabled || agentBaseUrl.isBlank()) {
					return "Agent 执行器尚未由运维启用";
				}
				if (!config.path("agentCode").asText().matches("[A-Za-z0-9._-]{1,100}")) {
					return "Agent 编码无效";
				}
			}
			if (!Set.of("spring-service", "message", "http-api", "ai-agent").contains(executor)) {
				return "执行器未注册：" + executor;
			}
			return null;
		} catch (IllegalArgumentException exception) {
			return exception.getMessage();
		}
	}

	public String execute(WorkflowNode node, String variables) {
		return execute(node, variables, "legacy", "legacy");
	}

	public String execute(
			WorkflowNode node,
			String variables,
			String instanceId,
			String executionId) {
		try {
			ObjectNode processVariables = object(variables, "流程变量");
			JsonNode config = tree(node.getPropertiesJson(), "节点扩展配置");
			JsonNode input = mapInput(processVariables, config.path("inputMapping"));
			validateSchema(input, node.getInputSchema(), "输入");

			JsonNode output = switch (String.valueOf(node.getExecutor())) {
			case "spring-service" -> executeInternal(config);
			case "message" -> executeMessage(config, processVariables, instanceId, node, executionId);
			case "http-api" -> executeHttp(config, input, node.getTimeoutSec());
			case "ai-agent" -> executeAgent(config, input, node.getTimeoutSec());
			default -> throw new IllegalArgumentException("执行器未注册：" + node.getExecutor());
			};

			validateSchema(output, node.getOutputSchema(), "输出");
			mergeOutput(processVariables, output, config.path("outputMapping"));
			return json.writeValueAsString(processVariables);
		} catch (IllegalArgumentException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new IllegalArgumentException("自动节点执行失败：" + exception.getMessage(), exception);
		}
	}

	/** 审计只保存脱敏后的变量，避免令牌、密码进入执行日志。 */
	public String redact(String value) {
		try {
			JsonNode root = tree(value, "审计数据");
			redactNode(root);
			return json.writeValueAsString(root);
		} catch (Exception exception) {
			return "{\"redacted\":true}";
		}
	}

	private JsonNode executeInternal(JsonNode config) {
		String key = config.path("outputKey").asText();
		if (key.isBlank()) {
			throw new IllegalArgumentException("内部服务节点必须配置 outputKey");
		}
		ObjectNode output = json.createObjectNode();
		output.set(key, config.path("value"));
		return output;
	}

	private JsonNode executeMessage(
			JsonNode config,
			ObjectNode variables,
			String instanceId,
			WorkflowNode node,
			String executionId) {
		String recipient = render(config.path("recipient").asText(), variables);
		if (recipient.isBlank()) {
			recipient = variables.path("chronosInitiator").asText();
		}
		if (recipient.isBlank()) {
			throw new IllegalArgumentException("消息节点必须配置接收人，或流程变量中包含 initiator");
		}
		String subject = render(config.path("subject").asText(node.getNodeName()), variables);
		String content = render(config.path("content").asText(), variables);
		notifications.enqueueWorkflowMessage(
				instanceId,
				node.getNodeKey(),
				recipient,
				subject,
				content,
				executionId);
		ObjectNode output = json.createObjectNode();
		output.put("recipient", recipient);
		output.put("queued", true);
		return output;
	}

	private JsonNode executeHttp(JsonNode config, JsonNode input, Integer timeoutSeconds) throws Exception {
		if (!httpEnabled) {
			throw new IllegalArgumentException("HTTP 执行器未由运维启用");
		}
		URI uri = requireAllowedUri(config.path("url").asText());
		String method = config.path("method").asText("POST").toUpperCase(Locale.ROOT);
		return sendJson(uri, method, config.path("headers"), input, timeoutSeconds);
	}

	private JsonNode executeAgent(JsonNode config, JsonNode input, Integer timeoutSeconds) throws Exception {
		if (!agentEnabled || agentBaseUrl.isBlank()) {
			throw new IllegalArgumentException("Agent 执行器未由运维启用");
		}
		String agentCode = config.path("agentCode").asText();
		if (!agentCode.matches("[A-Za-z0-9._-]{1,100}")) {
			throw new IllegalArgumentException("Agent 编码无效");
		}
		URI uri = URI.create(agentBaseUrl + "/api/agents/" + agentCode + "/execute");
		return sendJson(uri, "POST", config.path("headers"), input, timeoutSeconds);
	}

	private JsonNode sendJson(
			URI uri,
			String method,
			JsonNode headers,
			JsonNode input,
			Integer timeoutSeconds) throws Exception {
		if (!Set.of("POST", "PUT", "PATCH").contains(method)) {
			throw new IllegalArgumentException("自动节点仅允许 POST、PUT、PATCH 方法");
		}
		validateHeaders(headers);
		int configuredTimeout = timeoutSeconds == null ? 0 : timeoutSeconds;
		int timeout = Math.max(1, configuredTimeout == 0 ? 30 : configuredTimeout);
		HttpRequest.Builder request = HttpRequest.newBuilder(uri)
				.timeout(Duration.ofSeconds(timeout))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json");
		if (headers.isObject()) {
			headers.fields().forEachRemaining(entry -> addHeader(request, entry));
		}
		request.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(input)));
		HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IllegalArgumentException("外部服务返回 HTTP " + response.statusCode());
		}
		if (response.body() == null || response.body().isBlank()) {
			return json.createObjectNode();
		}
		return json.readTree(response.body());
	}

	private void addHeader(HttpRequest.Builder request, java.util.Map.Entry<String, JsonNode> entry) {
		String name = entry.getKey();
		String normalized = name.toLowerCase(Locale.ROOT);
		if (!Set.of("host", "content-length").contains(normalized)) {
			request.header(name, entry.getValue().asText());
		}
	}

	private void validateHeaders(JsonNode headers) {
		if (!headers.isObject()) {
			return;
		}
		headers.fieldNames().forEachRemaining(name -> {
			String normalized = name.toLowerCase(Locale.ROOT);
			if (Set.of("authorization", "cookie", "x-api-key", "proxy-authorization").contains(normalized)) {
				throw new IllegalArgumentException(
						"认证密钥不能保存在流程定义中，请通过集成网关注入：" + name);
			}
		});
	}

	private URI requireAllowedUri(String url) {
		URI uri;
		try {
			uri = URI.create(url);
		} catch (Exception exception) {
			throw new IllegalArgumentException("HTTP 地址无效");
		}
		if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
			throw new IllegalArgumentException("HTTP 自动节点只允许 HTTPS 地址");
		}
		if (!allowedHosts.contains(uri.getHost().toLowerCase(Locale.ROOT))) {
			throw new IllegalArgumentException("HTTP 地址不在运维白名单：" + uri.getHost());
		}
		return uri;
	}

	private JsonNode mapInput(ObjectNode variables, JsonNode mapping) {
		if (!mapping.isObject() || mapping.isEmpty()) {
			return variables.deepCopy();
		}
		ObjectNode input = json.createObjectNode();
		mapping.fields().forEachRemaining(entry -> {
			String source = entry.getValue().asText();
			JsonNode value = source.startsWith("$")
					? variables.at(toPointer(source.substring(1)))
					: entry.getValue();
			input.set(entry.getKey(), value.isMissingNode() ? json.nullNode() : value.deepCopy());
		});
		return input;
	}

	private void mergeOutput(ObjectNode variables, JsonNode output, JsonNode mapping) {
		if (mapping.isObject() && !mapping.isEmpty()) {
			mapping.fields().forEachRemaining(entry -> {
				JsonNode value = output.at(toPointer(entry.getKey()));
				variables.set(entry.getValue().asText(), value.isMissingNode() ? json.nullNode() : value.deepCopy());
			});
			return;
		}
		if (output.isObject()) {
			output.fields().forEachRemaining(entry -> variables.set(entry.getKey(), entry.getValue().deepCopy()));
		} else {
			variables.set("lastExecutorResult", output.deepCopy());
		}
	}

	private void validateSchema(JsonNode value, String schemaText, String direction) {
		if (schemaText == null || schemaText.isBlank()) {
			return;
		}
		JsonNode schema = tree(schemaText, direction + " Schema");
		if ("object".equals(schema.path("type").asText()) && !value.isObject()) {
			throw new IllegalArgumentException(direction + "数据必须是对象");
		}
		List<String> missing = new ArrayList<>();
		schema.path("required").forEach(field -> {
			String name = field.asText();
			if (!value.hasNonNull(name)) {
				missing.add(name);
			}
		});
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(direction + "数据缺少必填字段：" + String.join(", ", missing));
		}
	}

	private ObjectNode object(String value, String label) {
		JsonNode node = tree(value, label);
		if (!node.isObject()) {
			throw new IllegalArgumentException(label + "必须是 JSON 对象");
		}
		return (ObjectNode) node;
	}

	private void requireObjectOrBlank(String value, String label) {
		if (value == null || value.isBlank()) {
			return;
		}
		requireObject(tree(value, label), label);
	}

	private void requireObject(JsonNode value, String label) {
		if (!value.isMissingNode() && !value.isObject()) {
			throw new IllegalArgumentException(label + "必须是 JSON 对象");
		}
	}

	private JsonNode tree(String value, String label) {
		try {
			return json.readTree(value == null || value.isBlank() ? "{}" : value);
		} catch (Exception exception) {
			throw new IllegalArgumentException(label + "不是合法 JSON", exception);
		}
	}

	private String render(String template, JsonNode variables) {
		Matcher matcher = TEMPLATE_VARIABLE.matcher(template == null ? "" : template);
		StringBuffer rendered = new StringBuffer();
		while (matcher.find()) {
			JsonNode value = variables.at(toPointer(matcher.group(1)));
			matcher.appendReplacement(rendered, Matcher.quoteReplacement(value.asText("")));
		}
		matcher.appendTail(rendered);
		return rendered.toString();
	}

	private String toPointer(String path) {
		return path == null || path.isBlank() ? "" : "/" + path.replace(".", "/");
	}

	private Set<String> parseHosts(String value) {
		Set<String> result = new LinkedHashSet<>();
		Arrays.stream(value == null ? new String[0] : value.split(","))
				.map(String::strip)
				.filter(item -> !item.isBlank())
				.map(item -> item.toLowerCase(Locale.ROOT))
				.forEach(result::add);
		return result;
	}

	private void redactNode(JsonNode node) {
		if (node == null || !node.isContainerNode()) {
			return;
		}
		if (node.isObject()) {
			node.fields().forEachRemaining(entry -> {
				String key = entry.getKey().toLowerCase(Locale.ROOT);
				if (key.contains("password")
						|| key.contains("secret")
						|| key.contains("token")
						|| key.contains("authorization")) {
					((ObjectNode) node).put(entry.getKey(), "***");
				} else {
					redactNode(entry.getValue());
				}
			});
		} else {
			node.forEach(this::redactNode);
		}
	}
}
