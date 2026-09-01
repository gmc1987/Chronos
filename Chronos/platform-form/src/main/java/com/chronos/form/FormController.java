package com.chronos.form;

import com.chronos.commons.model.ResultData;
import com.chronos.model.form.*;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class FormController {
	private final FormService service;

	public FormController(FormService service) {
		this.service = service;
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	@GetMapping("/admin/forms/list")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Page<FormDefinition>> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size) {
		return ok(service.list(PageRequest.of(page, Math.min(size, 200), Sort.by("formName"))));
	}

	@PostMapping("/admin/forms")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<FormDefinition> create(@RequestBody FormDefinition v) {
		return ok(service.save(v));
	}

	@PutMapping("/admin/forms")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<FormDefinition> update(@RequestBody FormDefinition v) {
		return ok(service.update(v));
	}

	@DeleteMapping("/admin/forms/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Void> delete(@PathVariable String id) {
		service.delete(id);
		return ok(null);
	}

	@PostMapping("/admin/forms/{id}/publish")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<FormDefinition> publish(@PathVariable String id) {
		return ok(service.publish(id));
	}

	@PostMapping("/admin/forms/{id}/versions")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<FormDefinition> createVersion(@PathVariable String id,
			@RequestBody java.util.Map<String, String> body) {
		return ok(service.createVersion(id, body.get("version")));
	}

	@GetMapping("/admin/form-fields/list")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<List<FormField>> fields(@RequestParam String formId) {
		return ok(service.fields(formId));
	}

	@PostMapping("/admin/form-fields")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<FormField> createField(@RequestBody FormField v) {
		return ok(service.saveField(v));
	}

	@PutMapping("/admin/form-fields")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<FormField> updateField(@RequestBody FormField v) {
		return ok(service.updateField(v));
	}

	@DeleteMapping("/admin/form-fields/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'workflow:manage')")
	public ResultData<Void> deleteField(@PathVariable String id) {
		service.deleteField(id);
		return ok(null);
	}
}
