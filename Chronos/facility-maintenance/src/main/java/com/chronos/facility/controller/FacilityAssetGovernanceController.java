package com.chronos.facility.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.facility.model.FacilityAsset;
import com.chronos.facility.model.FacilitySafetyIncident;
import com.chronos.facility.model.InventoryTransaction;
import com.chronos.facility.model.PreventiveMaintenancePlan;
import com.chronos.facility.service.FacilityAssetGovernanceService;
import com.chronos.facility.service.FacilityAssetGovernanceService.AssetCommand;
import com.chronos.facility.service.FacilityAssetGovernanceService.IncidentCommand;
import com.chronos.facility.service.FacilityAssetGovernanceService.InventoryCommand;
import com.chronos.facility.service.FacilityAssetGovernanceService.PlanCommand;
import java.security.Principal;
import java.util.List;
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
@RequestMapping("/admin/facility/governance")
public class FacilityAssetGovernanceController {
	private final FacilityAssetGovernanceService service;

	public FacilityAssetGovernanceController(FacilityAssetGovernanceService service) {
		this.service = service;
	}

	@GetMapping("/assets")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<List<FacilityAsset>> assets() {
		return ok(service.assets());
	}

	@GetMapping("/assets/by-qr")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<FacilityAsset> assetByQr(@RequestParam String token) {
		return ok(service.assetByQr(token));
	}

	@PostMapping("/assets")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<FacilityAsset> createAsset(@RequestBody AssetCommand command) {
		return ok(service.saveAsset(null, command));
	}

	@PutMapping("/assets/{id}")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<FacilityAsset> updateAsset(@PathVariable String id, @RequestBody AssetCommand command) {
		return ok(service.saveAsset(id, command));
	}

	@GetMapping("/preventive-plans")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<List<PreventiveMaintenancePlan>> plans() {
		return ok(service.plans());
	}

	@PostMapping("/preventive-plans")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<PreventiveMaintenancePlan> createPlan(@RequestBody PlanCommand command) {
		return ok(service.createPlan(command));
	}

	@PostMapping("/preventive-plans/{id}/complete")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<PreventiveMaintenancePlan> completePlan(
			@PathVariable String id,
			@RequestBody NoteCommand command,
			Principal principal) {
		return ok(service.completePlan(id, command.note(), principal.getName()));
	}

	@GetMapping("/inventory-transactions")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:material:view','facility:material:manage')")
	public ResultData<List<InventoryTransaction>> transactions() {
		return ok(service.transactions());
	}

	@PostMapping("/inventory-transactions")
	@PreAuthorize("hasAuthority('facility:material:manage')")
	public ResultData<InventoryTransaction> adjustInventory(
			@RequestBody InventoryCommand command,
			Principal principal) {
		return ok(service.adjustInventory(command, principal.getName()));
	}

	@GetMapping("/safety-incidents")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<List<FacilitySafetyIncident>> incidents() {
		return ok(service.incidents());
	}

	@PostMapping("/safety-incidents")
	@PreAuthorize("hasAuthority('facility:safety:manage')")
	public ResultData<FacilitySafetyIncident> reportIncident(
			@RequestBody IncidentCommand command,
			Principal principal) {
		return ok(service.reportIncident(command, principal.getName()));
	}

	@PostMapping("/safety-incidents/{id}/close")
	@PreAuthorize("hasAuthority('facility:safety:manage')")
	public ResultData<FacilitySafetyIncident> closeIncident(
			@PathVariable String id,
			@RequestBody NoteCommand command,
			Principal principal) {
		return ok(service.closeIncident(id, command.note(), principal.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	public record NoteCommand(String note) {
	}
}
