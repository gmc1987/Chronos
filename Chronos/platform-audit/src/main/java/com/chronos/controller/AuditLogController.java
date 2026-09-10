package com.chronos.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.model.pojo.AuditLog;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.impl.AuditLogExportService;
import com.chronos.service.impl.AuditLogQueryService;
import com.chronos.service.impl.AuditLogQueryService.AuditLogQuery;

@RestController
@RequestMapping("/admin/audit-logs")
@PreAuthorize("@iamAuthorization.has(authentication,'iam:audit:view')")
public class AuditLogController {
	private final AuditLogQueryService service;
	private final AuditLogExportService exportService;
	private final IAuditLogService auditLogService;

	public AuditLogController(
			AuditLogQueryService service,
			AuditLogExportService exportService,
			IAuditLogService auditLogService) {
		this.service = service;
		this.exportService = exportService;
		this.auditLogService = auditLogService;
	}

	@GetMapping
	public ResultData<AuditLogPage> query(
			@RequestParam(defaultValue = "ALL") String domain,
			@RequestParam(required = false) String username,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		validateTimeRange(startTime, endTime);
		Page<AuditLog> result = service.query(
				new AuditLogQuery(
						domain,
						username,
						action,
						keyword,
						startTime,
						endTime),
				page,
				size);
		return ResultData.<AuditLogPage>builder()
				.code("200")
				.msg("success")
				.data(AuditLogPage.from(result))
				.build();
	}

	@GetMapping("/export")
	@PreAuthorize("@iamAuthorization.has(authentication,'iam:audit:view')"
			+ " and @iamAuthorization.has(authentication,'iam:audit:export')")
	public ResponseEntity<byte[]> export(
			@RequestParam(defaultValue = "ALL") String domain,
			@RequestParam(required = false) String username,
			@RequestParam(required = false) String action,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
			Authentication authentication) {
		validateTimeRange(startTime, endTime);
		AuditLogQuery query = new AuditLogQuery(
				domain,
				username,
				action,
				keyword,
				startTime,
				endTime);
		byte[] content = exportService.export(query);
		auditLogService.log(
				authentication.getName(),
				"AUDIT_LOG_EXPORT",
				"domain=" + domain + ", bytes=" + content.length);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
		headers.setContentDisposition(ContentDisposition.attachment()
				.filename("chronos-audit.csv", StandardCharsets.UTF_8)
				.build());
		return ResponseEntity.ok()
				.headers(headers)
				.body(content);
	}

	private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
		if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
			throw new IllegalArgumentException("开始时间不能晚于结束时间");
		}
	}

	/** 固定分页契约，避免直接序列化 Spring Data Page 产生不稳定响应结构。 */
	public record AuditLogPage(
			List<AuditLog> content,
			long totalElements,
			int totalPages,
			int number,
			int size) {
		static AuditLogPage from(Page<AuditLog> page) {
			return new AuditLogPage(
					page.getContent(),
					page.getTotalElements(),
					page.getTotalPages(),
					page.getNumber(),
					page.getSize());
		}
	}
}
