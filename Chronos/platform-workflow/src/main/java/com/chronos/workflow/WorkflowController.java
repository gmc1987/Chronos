package com.chronos.workflow;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.model.form.FormInstance;
import com.chronos.model.workflow.WorkflowAiSetting;
import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowEdge;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.model.workflow.WorkflowNode;
import com.chronos.model.workflow.WorkflowReview;
import com.chronos.model.workflow.WorkflowTask;
import com.chronos.workflow.executor.ExecutorDescriptor;
import com.chronos.workflow.executor.WorkflowExecutorRegistry;

@RestController
public class WorkflowController {
	private final WorkflowService service;
	private final WorkflowExecutorRegistry executors;

	public WorkflowController(WorkflowService service, WorkflowExecutorRegistry executors) {
		this.service = service;
		this.executors = executors;
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	@GetMapping("/admin/workflows/list")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Page<WorkflowDefinition>> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ok(service.list(PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createTime"))));
	}

	@GetMapping("/admin/workflows/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowDefinition> get(@PathVariable String id) {
		return ok(service.get(id));
	}

	@PostMapping("/admin/workflows")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowDefinition> create(@RequestBody WorkflowDefinition v, Principal p) {
		v.setId(null);
		return ok(service.save(v, p.getName()));
	}

	@PutMapping("/admin/workflows")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowDefinition> update(@RequestBody WorkflowDefinition v, Principal p) {
		return ok(service.update(v, p.getName()));
	}

	@DeleteMapping("/admin/workflows/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Void> delete(@PathVariable String id, Principal p) {
		service.delete(id, p.getName());
		return ok(null);
	}

	@PostMapping("/admin/workflows/{id}/disable")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowDefinition> disable(@PathVariable String id, Principal p) {
		return ok(service.disable(id, p.getName()));
	}

	@PostMapping("/admin/workflows/{id}/versions")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowDefinition> createVersion(@PathVariable String id, @RequestBody Map<String, String> body,
			Principal p) {
		return ok(service.createVersion(id, body.get("version"), p.getName()));
	}

	@GetMapping("/admin/workflow-nodes/list")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<List<WorkflowNode>> nodes(@RequestParam String flowId) {
		return ok(service.nodes(flowId));
	}

	@PostMapping("/admin/workflow-nodes")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowNode> createNode(@RequestBody WorkflowNode v) {
		v.setId(null);
		return ok(service.saveNode(v));
	}

	@PutMapping("/admin/workflow-nodes")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowNode> updateNode(@RequestBody WorkflowNode v) {
		return ok(service.updateNode(v));
	}

	@DeleteMapping("/admin/workflow-nodes/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Void> deleteNode(@PathVariable String id) {
		service.deleteNode(id);
		return ok(null);
	}

	@GetMapping("/admin/workflow-edges/list")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<List<WorkflowEdge>> edges(@RequestParam String flowId) {
		return ok(service.edges(flowId));
	}

	@PostMapping("/admin/workflow-edges")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowEdge> createEdge(@RequestBody WorkflowEdge v) {
		v.setId(null);
		return ok(service.saveEdge(v));
	}

	@PutMapping("/admin/workflow-edges")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowEdge> updateEdge(@RequestBody WorkflowEdge v) {
		return ok(service.updateEdge(v));
	}

	@DeleteMapping("/admin/workflow-edges/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Void> deleteEdge(@PathVariable String id) {
		service.deleteEdge(id);
		return ok(null);
	}

	@PostMapping("/admin/workflows/{id}/validate")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<List<WorkflowReview>> validate(@PathVariable String id,
			@RequestParam(defaultValue = "false") boolean ai, Principal p) {
		return ok(service.validate(id, ai, p.getName()));
	}

	@PostMapping("/admin/workflows/{id}/publish")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowDefinition> publish(@PathVariable String id, Principal p) {
		return ok(service.publish(id, p.getName()));
	}

	@GetMapping("/admin/workflow-ai/settings")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowAiSetting> setting() {
		return ok(service.setting());
	}

	@PutMapping("/admin/workflow-ai/settings")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<WorkflowAiSetting> setting(@RequestBody WorkflowAiSetting v, Principal p) {
		return ok(service.updateSetting(v, p.getName()));
	}

	@PostMapping("/admin/workflow-ai/draft")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Map<String, Object>> draft(@RequestBody Map<String, String> body) {
		return ok(service.aiDraft(body.get("requirement")));
	}

	@GetMapping("/admin/workflow-executors")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<List<ExecutorDescriptor>> executors() {
		return ok(executors.descriptors());
	}

	@PostMapping("/workflows/{id}/start")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowInstance> start(@PathVariable String id,
			@RequestBody(required = false) Map<String, Object> body, Principal p) {
		Map<String, Object> b = body == null ? Map.of() : body;
		Object raw = b.get("formData");
		Map<String, Object> formData = raw instanceof Map<?, ?> map
				? map.entrySet().stream().collect(
						java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue))
				: Map.of();
		return ok(service.start(id, (String) b.get("businessKey"),
				b.get("variablesJson") == null ? "{}" : String.valueOf(b.get("variablesJson")), formData, p.getName()));
	}

	@GetMapping("/workflows/available")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<List<WorkflowDefinition>> available() { return ok(service.available()); }

	@GetMapping("/workflows/{id}/start-form")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<Map<String,Object>> startForm(@PathVariable String id) { return ok(service.startForm(id)); }

	@GetMapping("/workflow-instances/{id}/forms")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<Map<String, Object>> runtimeForms(@PathVariable String id, Principal p) {
		return ok(service.runtimeForms(id, p.getName()));
	}

	@PutMapping("/workflow-instances/{id}/forms/{formId}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<FormInstance> saveRuntimeForm(@PathVariable String id, @PathVariable String formId,
			@RequestBody Map<String, Object> body, Principal p) {
		Object raw = body.get("data");
		Map<String, Object> data = raw instanceof Map<?, ?> map
				? map.entrySet().stream().collect(
						java.util.stream.Collectors.toMap(e -> String.valueOf(e.getKey()), Map.Entry::getValue))
				: Map.of();
		return ok(service.saveRuntimeForm(id, formId, data, Boolean.TRUE.equals(body.get("draft")), p.getName()));
	}

	@GetMapping("/workflow-tasks/pending")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<List<WorkflowTask>> pending(Principal p) {
		return ok(service.pending(p.getName()));
	}

	@GetMapping("/workflow-tasks/handled") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<List<WorkflowTask>> handled(Principal p) { return ok(service.handled(p.getName())); }

	@GetMapping("/workflow-instances/initiated") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<List<WorkflowInstance>> initiated(Principal p) { return ok(service.initiated(p.getName())); }

	@PostMapping("/workflow-tasks/{id}/remind") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowTask> remind(@PathVariable String id,Principal p){return ok(service.remind(id,p.getName()));}

	@GetMapping("/admin/workflows/monitor") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Map<String,Object>> monitor(){return ok(service.monitor());}

	@PostMapping("/workflow-tasks/{id}/transfer") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowTask> transfer(@PathVariable String id,@RequestBody Map<String,String> body,Principal p){return ok(service.transferTask(id,body.get("assignee"),body.get("comment"),p.getName()));}

	@PostMapping("/workflow-tasks/{id}/add-sign") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowTask> addSign(@PathVariable String id,@RequestBody Map<String,String> body,Principal p){return ok(service.addSign(id,body.get("assignee"),body.get("comment"),p.getName()));}

	@PostMapping("/workflow-tasks/{id}/cc") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowTask> cc(@PathVariable String id,@RequestBody Map<String,String> body,Principal p){return ok(service.ccTask(id,body.get("assignee"),body.get("comment"),p.getName()));}

	@PostMapping("/workflow-tasks/{id}/return") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowInstance> returnTask(@PathVariable String id,@RequestBody Map<String,String> body,Principal p){return ok(service.returnTask(id,body.get("comment"),p.getName()));}

	@PostMapping("/workflow-instances/{id}/withdraw") @PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowInstance> withdraw(@PathVariable String id,@RequestBody(required=false) Map<String,String> body,Principal p){return ok(service.withdraw(id,body==null?null:body.get("comment"),p.getName()));}

	@PostMapping("/workflow-tasks/{id}/complete")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:use')")
	public ResultData<WorkflowInstance> complete(@PathVariable String id, @RequestBody Map<String, Object> body,
			Principal p) {
		return ok(service.completeTask(id, Boolean.TRUE.equals(body.get("approved")),
				String.valueOf(body.getOrDefault("comment", "")), p.getName()));
	}
}
