package com.chronos.delivery;

import com.chronos.commons.model.ResultData;
import java.security.Principal;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sales-delivery")
@Conditional(DeliveryMode.IssuerCondition.class)

class SalesDeliveryController {
	private final SalesDeliveryService service;

	SalesDeliveryController(SalesDeliveryService service) {
		this.service = service;
	}

	@GetMapping("/status")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:view')")
	ResultData<?> status() {
		return ok(java.util.Map.of("signingKeyConfigured", service.signingKeyConfigured()));
	}

	@GetMapping("/catalog")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:view')")
	ResultData<?> catalog() {
		return ok(service.catalog());
	}

	@PutMapping("/catalog/{code}")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:catalog:manage')")
	ResultData<?> updateCatalog(@PathVariable String code,
			@RequestBody SalesDeliveryService.CatalogSettings settings, Principal actor) {
		service.updateCatalog(code, settings, actor.getName());
		return ok(null);
	}

	@GetMapping("/orders")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:view')")
	ResultData<?> orders(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ok(service.orders(keyword, page, size));
	}

	@PostMapping("/orders")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:edit')")
	ResultData<?> create(@RequestBody SalesDeliveryService.Order order, Principal actor) {
		return ok(service.create(order, actor.getName()));
	}

	@PostMapping("/orders/{id}/confirm")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:confirm')")
	ResultData<?> confirm(@PathVariable UUID id, Principal actor) {
		service.confirm(id, actor.getName());
		return ok(null);
	}

	@GetMapping("/orders/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:view')")
	ResultData<?> order(@PathVariable UUID id) {
		return ok(service.detail(id));
	}

	@PostMapping("/orders/{id}/deployments")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:edit')")
	ResultData<?> deployment(@PathVariable UUID id, @RequestParam(required = false) UUID deploymentId,
			@RequestBody SalesDeliveryService.Deployment command, Principal actor) {
		return ok(service.configure(id, deploymentId, command, actor.getName()));
	}

	@GetMapping("/deployments/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:view')")
	ResultData<?> deployment(@PathVariable UUID id) {
		return ok(service.deployment(id));
	}

	@PostMapping("/deployments/{id}/licenses")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:issue')")
	ResultData<?> license(@PathVariable UUID id, @RequestParam UUID orderId, Principal actor) {
		return ok(service.sign(id, orderId, actor.getName()));
	}

	@GetMapping("/deployments/{id}/licenses/{revision}")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:download')")
	ResponseEntity<String> license(@PathVariable UUID id, @PathVariable int revision) {
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=chronos-license.json")
				.header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.APPLICATION_JSON)
				.body(service.signedDocument(id, revision));
	}

	@PostMapping("/deployments/{id}/packages")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:package')")
	ResultData<?> queue(@PathVariable UUID id, @RequestParam int configRevision, @RequestParam int licenseRevision,
			Principal actor) {
		return ok(service.queue(id, configRevision, licenseRevision, actor.getName()));
	}

	@GetMapping("/packages/{id}/download")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:download')")
	ResponseEntity<FileSystemResource> download(@PathVariable UUID id, @RequestParam(defaultValue = "false") boolean checksum) {
		var path = service.archive(id, checksum);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + path.getFileName())
				.header(HttpHeaders.CACHE_CONTROL, "no-store").contentType(MediaType.APPLICATION_OCTET_STREAM)
				.body(new FileSystemResource(path));
	}

	@PostMapping("/packages/{id}/events")
	@PreAuthorize("@iamAuthorization.has(authentication,'delivery:sales:record')")
	ResultData<?> event(@PathVariable UUID id, @RequestBody SalesDeliveryService.DeliveryEvent command, Principal actor) {
		service.record(id, command, actor.getName());
		return ok(null);
	}

	static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
