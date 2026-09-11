package com.chronos.message.controller;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.commons.model.ResultData;
import com.chronos.commons.model.PageView;
import com.chronos.message.model.PublicationCommand;
import com.chronos.message.model.PublicationView;
import com.chronos.message.model.PublicationStatistics;
import com.chronos.message.model.PublicationVersion;
import com.chronos.message.service.iService.IPublicationService;

@RestController
public class PublicationController {
	private final IPublicationService service;

	public PublicationController(IPublicationService service) {
		this.service = service;
	}

	@GetMapping("/admin/publications")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:view','message:publication:manage')")
	public ResultData<PageView<PublicationView>> list(@RequestParam(required = false) String type,
			@RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			Principal principal) {
		return ok(PageView.from(service.adminList(
				type,
				status,
				keyword,
				principal.getName(),
				PageRequest.of(
						Math.max(page, 0),
						Math.min(Math.max(size, 1), 100),
						Sort.by(Sort.Direction.DESC, "createTime")))));
	}

	@GetMapping("/admin/publications/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:view','message:publication:manage')")
	public ResultData<PublicationView> detail(@PathVariable String id, Principal principal) {
		return ok(service.adminDetail(id, principal.getName()));
	}

	@GetMapping("/admin/publications/{id}/statistics")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:statistics','message:publication:manage')")
	public ResultData<PublicationStatistics> statistics(@PathVariable String id, Principal principal) {
		return ok(service.statistics(id, principal.getName()));
	}

	@GetMapping("/admin/publications/{id}/versions")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:view','message:publication:manage')")
	public ResultData<List<PublicationVersion>> versions(@PathVariable String id, Principal principal) {
		return ok(service.versions(id, principal.getName()));
	}

	@PostMapping("/admin/publications/{id}/remind-unread")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:statistics','message:publication:manage')")
	public ResultData<Long> remindUnread(@PathVariable String id, Principal principal) {
		return ok(service.remindUnread(id, principal.getName()));
	}

	@PostMapping("/admin/publications")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:create','message:publication:manage')")
	public ResultData<PublicationView> create(@RequestBody PublicationCommand command, Principal principal) {
		return ok(service.create(command, principal.getName()));
	}

	@PutMapping("/admin/publications/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:update','message:publication:manage')")
	public ResultData<PublicationView> update(@PathVariable String id, @RequestBody PublicationCommand command,
			Principal principal) {
		return ok(service.update(id, command, principal.getName()));
	}

	@DeleteMapping("/admin/publications/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:delete','message:publication:manage')")
	public ResultData<Void> delete(@PathVariable String id, Principal principal) {
		service.delete(id, principal.getName());
		return ok(null);
	}

	@PostMapping("/admin/publications/{id}/publish")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:publish','message:publication:manage')")
	public ResultData<PublicationView> publish(@PathVariable String id, Principal principal) {
		return ok(service.publish(id, principal.getName()));
	}

	@PostMapping("/admin/publications/{id}/submit")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:update','message:publication:manage')")
	public ResultData<PublicationView> submit(@PathVariable String id, Principal principal) {
		return ok(service.submit(id, principal.getName()));
	}

	@PostMapping("/admin/publications/{id}/approve")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:review','message:publication:manage')")
	public ResultData<PublicationView> approve(@PathVariable String id,
			@RequestBody(required = false) java.util.Map<String, String> body, Principal principal) {
		return ok(service.approve(id, body == null ? null : body.get("comment"), principal.getName()));
	}

	@PostMapping("/admin/publications/{id}/reject")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:review','message:publication:manage')")
	public ResultData<PublicationView> reject(@PathVariable String id,
			@RequestBody(required = false) java.util.Map<String, String> body, Principal principal) {
		return ok(service.reject(id, body == null ? null : body.get("comment"), principal.getName()));
	}

	@PostMapping("/admin/publications/{id}/withdraw")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:withdraw','message:publication:manage')")
	public ResultData<PublicationView> withdraw(@PathVariable String id, Principal principal) {
		return ok(service.withdraw(id, principal.getName()));
	}

	@PostMapping(value = "/admin/publications/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:update','message:publication:manage')")
	public ResultData<PublicationView> attach(@PathVariable String id, @RequestParam MultipartFile file,
			@RequestParam(defaultValue = "false") boolean primaryContent, Principal principal) {
		return ok(service.attach(id, file, primaryContent, principal.getName()));
	}

	@DeleteMapping("/admin/publications/attachments/{attachmentId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:update','message:publication:manage')")
	public ResultData<Void> deleteAttachment(@PathVariable String attachmentId, Principal principal) {
		service.deleteAttachment(attachmentId, principal.getName());
		return ok(null);
	}

	@GetMapping("/publications")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:read')")
	public ResultData<PageView<PublicationView>> visible(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "false") boolean unreadOnly,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Principal principal) {
		PageRequest pageable = PageRequest.of(
				Math.max(page, 0),
				Math.min(Math.max(size, 1), 100),
				Sort.by(
						Sort.Order.desc("pinned"),
						Sort.Order.asc("sortOrder"),
						Sort.Order.desc("publishedAt")));
		return ok(PageView.from(service.visible(
				principal.getName(),
				type,
				keyword,
				unreadOnly,
				pageable)));
	}

	@PostMapping("/admin/publications/{id}/versions/{versionNo}/restore")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:update','message:publication:manage')")
	public ResultData<PublicationView> restoreVersion(
			@PathVariable String id,
			@PathVariable Integer versionNo,
			Principal principal) {
		return ok(service.restoreVersion(id, versionNo, principal.getName()));
	}

	@PostMapping("/admin/publications/{id}/archive")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:update','message:publication:manage')")
	public ResultData<PublicationView> archive(
			@PathVariable String id,
			@RequestParam(defaultValue = "true") boolean archived,
			Principal principal) {
		return ok(service.archive(id, archived, principal.getName()));
	}

	@GetMapping("/publications/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:read')")
	public ResultData<PublicationView> portalDetail(@PathVariable String id, Principal principal) {
		return ok(service.portalDetail(id, principal.getName()));
	}

	@PostMapping("/publications/{id}/read")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:read')")
	public ResultData<Void> markRead(@PathVariable String id, Principal principal) {
		service.markRead(id, principal.getName());
		return ok(null);
	}

	@GetMapping("/publications/attachments/{attachmentId}")
	@PreAuthorize("@iamAuthorization.has(authentication,'message:publication:read')")
	public ResponseEntity<byte[]> portalDownload(@PathVariable String attachmentId, Principal principal) {
		return download(service.download(attachmentId, principal.getName(), false));
	}

	@GetMapping("/admin/publications/attachments/{attachmentId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'message:publication:view','message:publication:manage')")
	public ResponseEntity<byte[]> adminDownload(@PathVariable String attachmentId, Principal principal) {
		return download(service.download(attachmentId, principal.getName(), true));
	}

	private ResponseEntity<byte[]> download(IPublicationService.DownloadFile file) {
		MediaType contentType;
		try {
			contentType = MediaType.parseMediaType(file.contentType());
		} catch (Exception ignored) {
			contentType = MediaType.APPLICATION_OCTET_STREAM;
		}
		return ResponseEntity.ok().contentType(contentType).header(HttpHeaders.CONTENT_DISPOSITION,
				ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build().toString())
				.body(file.content());
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
