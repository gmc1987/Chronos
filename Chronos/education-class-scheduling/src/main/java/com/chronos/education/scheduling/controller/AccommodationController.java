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

@RestController
@RequestMapping("/admin/education/accommodations")
public class AccommodationController {
	private final AccommodationService service;
	private final AccommodationImportService imports;

	public AccommodationController(AccommodationService service, AccommodationImportService imports) { this.service = service; this.imports = imports; }

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
			@RequestParam(defaultValue = "true") boolean dryRun) {
		return ok(imports.importFile(file, dryRun));
	}

	@GetMapping("/buildings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> buildings() { return ok(service.buildings()); }

	@PostMapping("/buildings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createBuilding(@RequestBody BuildingCommand command) { return ok(service.createBuilding(command)); }

	@GetMapping("/buildings/{buildingId}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> rooms(@PathVariable String buildingId) { return ok(service.rooms(buildingId)); }

	@PostMapping("/buildings/{buildingId}/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createRoom(@PathVariable String buildingId, @RequestBody RoomCommand command) { return ok(service.createRoom(buildingId, command)); }

	@PutMapping("/rooms/{roomId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> updateRoom(@PathVariable String roomId, @RequestBody RoomCommand command) { return ok(service.updateRoom(roomId, command)); }

	@DeleteMapping("/rooms/{roomId}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Void> deleteRoom(@PathVariable String roomId, Authentication authentication) {
		service.deleteRoom(roomId, authentication.getName());
		return ok(null);
	}

	@PostMapping("/rooms/{roomId}/beds")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> createBed(@PathVariable String roomId, @RequestBody BedCommand command) { return ok(service.createBed(roomId, command)); }

	@GetMapping("/beds")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> beds(@RequestParam String semesterCode, @RequestParam(defaultValue = "false") boolean availableOnly) { return ok(service.beds(semesterCode, availableOnly)); }

	@GetMapping("/assignments")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> assignments(@RequestParam String semesterCode) { return ok(service.assignments(semesterCode)); }

	@GetMapping("/student-options")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:view','education:accommodation:manage')")
	public ResultData<List<Map<String, Object>>> studentOptions(@RequestParam(defaultValue = "") String keyword) { return ok(service.studentOptions(keyword)); }

	@PostMapping("/assign")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Map<String, Object>> assign(@RequestBody AssignmentCommand command, Authentication authentication) { return ok(service.assign(command, authentication.getName())); }

	@DeleteMapping("/assignments/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:accommodation:manage')")
	public ResultData<Void> cancel(@PathVariable String id, Authentication authentication) {
		service.cancelAssignment(id, authentication.getName());
		return ok(null);
	}

	private <T> ResultData<T> ok(T data) { ResultData<T> result = new ResultData<>(); result.setCode("200"); result.setMsg("success"); result.setData(data); return result; }
}
