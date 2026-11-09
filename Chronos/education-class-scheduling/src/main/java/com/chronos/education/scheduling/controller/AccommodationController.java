package com.chronos.education.scheduling.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.AccommodationService;
import com.chronos.education.scheduling.service.AccommodationService.AssignmentCommand;
import com.chronos.education.scheduling.service.AccommodationService.BedCommand;
import com.chronos.education.scheduling.service.AccommodationService.BuildingCommand;
import com.chronos.education.scheduling.service.AccommodationService.RoomCommand;
import com.chronos.education.scheduling.service.AccommodationImportService;
import com.chronos.security.DirectoryDataScopeGuard;

@RestController
@RequestMapping("/admin/education/accommodations")
public class AccommodationController {
	private final AccommodationService service;
	private final AccommodationImportService imports;
	private final DirectoryDataScopeGuard dataScopes;

	public AccommodationController(AccommodationService service, AccommodationImportService imports,
			DirectoryDataScopeGuard dataScopes) { this.service = service; this.imports = imports; this.dataScopes = dataScopes; }

	@GetMapping(value = "/import-template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResponseEntity<byte[]> importTemplate() {
		String filename = java.net.URLEncoder.encode("Chronos-宿舍资源批量导入模板.xlsx", StandardCharsets.UTF_8);
		return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
				.contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.body(imports.template());
	}

	@PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<?> importFile(@RequestPart("file") MultipartFile file,
			@RequestParam(defaultValue = "true") boolean dryRun,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		String resolved = resolveOrganization(authentication, organizationId);
		return ok(imports.importFile(file, dryRun, resolved));
	}

	@GetMapping("/buildings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> buildings(
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.buildings(selectedOrganizations(authentication, organizationId)));
	}

	@PostMapping("/buildings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createBuilding(@RequestBody BuildingCommand command, Authentication authentication) {
		return ok(service.createBuilding(command, resolveOrganization(authentication, command.organizationId())));
	}

	@GetMapping("/buildings/{buildingId}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> rooms(@PathVariable String buildingId, Authentication authentication) {
		requireOrganization(authentication, service.buildingOrganization(buildingId)); return ok(service.rooms(buildingId));
	}

	@PostMapping("/buildings/{buildingId}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createRoom(@PathVariable String buildingId, @RequestBody RoomCommand command, Authentication authentication) {
		requireOrganization(authentication, service.buildingOrganization(buildingId)); return ok(service.createRoom(buildingId, command));
	}

	@PutMapping("/rooms/{roomId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> updateRoom(@PathVariable String roomId, @RequestBody RoomCommand command, Authentication authentication) {
		requireOrganization(authentication, service.roomOrganization(roomId)); return ok(service.updateRoom(roomId, command));
	}

	@DeleteMapping("/rooms/{roomId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Void> deleteRoom(@PathVariable String roomId, Authentication authentication) {
		requireOrganization(authentication, service.roomOrganization(roomId));
		service.deleteRoom(roomId, authentication.getName());
		return ok(null);
	}

	@PostMapping("/rooms/{roomId}/beds")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createBed(@PathVariable String roomId, @RequestBody BedCommand command, Authentication authentication) {
		requireOrganization(authentication, service.roomOrganization(roomId)); return ok(service.createBed(roomId, command));
	}

	@GetMapping("/beds")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> beds(@RequestParam String semesterCode,
			@RequestParam(defaultValue = "false") boolean availableOnly,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.beds(semesterCode, availableOnly, selectedOrganizations(authentication, organizationId)));
	}

	@GetMapping("/assignments")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> assignments(@RequestParam String semesterCode,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.assignments(semesterCode, selectedOrganizations(authentication, organizationId)));
	}

	@GetMapping("/student-options")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> studentOptions(@RequestParam(defaultValue = "") String keyword,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		return ok(service.studentOptions(keyword, selectedOrganizations(authentication, organizationId)));
	}

	@PostMapping("/assign")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> assign(@RequestBody AssignmentCommand command, Authentication authentication) {
		requireOrganization(authentication, service.bedOrganization(command.bedId()));
		requireOrganization(authentication, service.studentOrganization(command.studentId()));
		return ok(service.assign(command, authentication.getName()));
	}

	@DeleteMapping("/assignments/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Void> cancel(@PathVariable String id, Authentication authentication) {
		requireOrganization(authentication, service.assignmentOrganization(id));
		service.cancelAssignment(id, authentication.getName());
		return ok(null);
	}
	private void requireOrganization(Authentication authentication, String organizationId) {
		dataScopes.requireOrganization(authentication, organizationId);
	}
	private String resolveOrganization(Authentication authentication, String requested) {
		if (requested != null && !requested.isBlank()) { requireOrganization(authentication, requested); return requested; }
		var visible = dataScopes.visibleOrganizationIds(authentication);
		if (visible != null && visible.size() == 1) return visible.iterator().next();
		throw new IllegalArgumentException("请选择宿舍资源所属机构");
	}
	private java.util.Collection<String> selectedOrganizations(Authentication authentication, String requested) {
		if (requested != null && !requested.isBlank()) {
			requireOrganization(authentication, requested);
			return java.util.Set.of(requested);
		}
		return dataScopes.visibleOrganizationIds(authentication);
	}

	private <T> ResultData<T> ok(T data) { ResultData<T> result = new ResultData<>(); result.setCode("200"); result.setMsg("success"); result.setData(data); return result; }
}
