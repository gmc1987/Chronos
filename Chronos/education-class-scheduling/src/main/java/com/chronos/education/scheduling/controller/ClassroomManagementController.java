package com.chronos.education.scheduling.controller;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.service.ClassroomImportService;
import com.chronos.education.scheduling.service.ClassroomManagementService;
import com.chronos.education.scheduling.service.ClassroomManagementService.Building;
import com.chronos.education.scheduling.service.ClassroomManagementService.Floor;
import com.chronos.security.DirectoryDataScopeGuard;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/education/classroom-management")
public class ClassroomManagementController {
	private final DirectoryDataScopeGuard directory;
	private final IOrganizationRepository organizations;
	private final ClassroomManagementService management;
	private final ClassroomImportService imports;

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:view','education:resource:venue:manage')")
	public ResultData<ClassroomManagementService.Catalog> catalog(@RequestParam String organizationId,
			@RequestParam(defaultValue = "") String buildingId, @RequestParam(defaultValue = "") String floorId,
			@RequestParam(defaultValue = "false") boolean unclassified,
			@RequestParam(required = false) Integer page, @RequestParam(defaultValue = "10") int size,
			Authentication actor) {
		return ok(management.catalog(campus(actor, organizationId), buildingId, floorId, unclassified, page, size));
	}

	@PostMapping("/buildings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:create','education:resource:venue:manage')")
	public ResultData<Building> createBuilding(@RequestParam String organizationId, @RequestBody Building command, Authentication actor) {
		return ok(management.saveBuilding(campus(actor, organizationId), new Building(null, command.buildingCode(), command.buildingName())));
	}

	@PutMapping("/buildings/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:update','education:resource:venue:manage')")
	public ResultData<Building> updateBuilding(@RequestParam String organizationId, @PathVariable String id,
			@RequestBody Building command, Authentication actor) {
		return ok(management.saveBuilding(campus(actor, organizationId), new Building(id, command.buildingCode(), command.buildingName())));
	}

	@PostMapping("/floors")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:create','education:resource:venue:manage')")
	public ResultData<Integer> createFloor(@RequestParam String organizationId,
			@RequestBody CreateFloors command, Authentication actor) {
		return ok(management.createFloorsThrough(campus(actor, organizationId), command.buildingId(), command.maxFloorNo()));
	}

	@PutMapping("/floors/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:update','education:resource:venue:manage')")
	public ResultData<Floor> updateFloor(@RequestParam String organizationId, @PathVariable String id,
			@RequestBody Floor command, Authentication actor) {
		return ok(management.saveFloor(campus(actor, organizationId), new Floor(id, command.buildingId(), command.floorNo())));
	}

	@DeleteMapping("/{kind:buildings|floors}/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:delete','education:resource:venue:manage')")
	public ResultData<Void> deleteLocation(@RequestParam String organizationId, @PathVariable String kind,
			@PathVariable String id, Authentication actor) {
		management.deleteLocation(campus(actor, organizationId), "buildings".equals(kind), id);
		return ok(null);
	}

	@PostMapping("/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:create','education:resource:venue:manage')")
	public ResultData<Classroom> createRoom(@RequestParam String organizationId, @RequestBody Classroom command, Authentication actor) {
		command.setId(null);
		return ok(management.saveRoom(campus(actor, organizationId), command));
	}

	@PutMapping("/rooms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:update','education:resource:venue:manage')")
	public ResultData<Classroom> updateRoom(@RequestParam String organizationId, @PathVariable String id,
			@RequestBody Classroom command, Authentication actor) {
		command.setId(id);
		return ok(management.saveRoom(campus(actor, organizationId), command));
	}

	@GetMapping("/import/template")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:create','education:resource:venue:manage')")
	public ResponseEntity<byte[]> template() throws IOException {
		return ResponseEntity.ok()
				.header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
				.header("Content-Disposition", "attachment; filename=classrooms.xlsx")
				.body(imports.template());
	}

	@PostMapping("/import")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:resource:venue:create','education:resource:venue:manage')")
	public ResultData<ClassroomImportService.Result> upload(@RequestParam String organizationId, @RequestParam MultipartFile file,
			@RequestParam(defaultValue = "true") boolean dryRun, Authentication actor) throws IOException {
		return ok(imports.run(campus(actor, organizationId), file, dryRun));
	}

	public record CreateFloors(String buildingId, Integer maxFloorNo) {}

	private String campus(Authentication actor, String id) {
		directory.requireOrganization(actor, id);
		var organization = organizations.findById(id).orElseThrow(() -> new IllegalArgumentException("校区不存在"));
		if (!"CAMPUS".equals(organization.getOrganizationType()) && !"SCHOOL".equals(organization.getOrganizationType())) {
			throw new IllegalArgumentException("请选择学校或校区");
		}
		return id;
	}

	private static <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
