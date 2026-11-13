package com.chronos.facility.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.facility.model.FacilityAsset;
import com.chronos.facility.model.FacilitySafetyIncident;
import com.chronos.facility.model.InventoryTransaction;
import com.chronos.facility.model.PreventiveMaintenancePlan;
import com.chronos.facility.service.FacilityAssetGovernanceService;
import com.chronos.facility.service.FacilityMaintenanceService;
import com.chronos.facility.service.FacilityAssetGovernanceService.AssetCommand;
import com.chronos.facility.service.FacilityAssetGovernanceService.IncidentCommand;
import com.chronos.facility.service.FacilityAssetGovernanceService.InventoryCommand;
import com.chronos.facility.service.FacilityAssetGovernanceService.PlanCommand;
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
import org.springframework.security.core.Authentication;
import com.chronos.security.DirectoryDataScopeGuard;

@RestController
@RequestMapping("/admin/facility/governance")
public class FacilityAssetGovernanceController {
	private final FacilityAssetGovernanceService service;
	private final DirectoryDataScopeGuard dataScopes;
	private final FacilityMaintenanceService maintenance;

	public FacilityAssetGovernanceController(FacilityAssetGovernanceService service, DirectoryDataScopeGuard dataScopes,
			FacilityMaintenanceService maintenance) {
		this.service = service;
		this.dataScopes = dataScopes;
		this.maintenance = maintenance;
	}

	@GetMapping("/assets")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<List<FacilityAsset>> assets(@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		return ok(service.assets(dataScopes.resolveQueryOrganizations(authentication, organizationId)));
	}

	@GetMapping("/assets/by-qr")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<FacilityAsset> assetByQr(@RequestParam String token,
			@RequestParam(required = false) String organizationId, Authentication authentication) {
		FacilityAsset value = service.assetByQr(token);
		dataScopes.requireOrganization(authentication, value.getOrganizationId());
		if (organizationId != null && !organizationId.equals(value.getOrganizationId())) {
			throw new IllegalArgumentException("资产不属于当前机构");
		}
		return ok(value);
	}

	@PostMapping("/assets")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<FacilityAsset> createAsset(@RequestBody AssetCommand command, Authentication authentication) {
		dataScopes.requireOrganization(authentication, command.organizationId());
		return ok(service.saveAsset(null, command));
	}

	@PutMapping("/assets/{id}")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<FacilityAsset> updateAsset(@PathVariable String id, @RequestBody AssetCommand command, Authentication authentication) {
		dataScopes.requireOrganization(authentication, service.assetOrganization(id));
		return ok(service.saveAsset(id, command));
	}

	@GetMapping("/preventive-plans")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<List<PreventiveMaintenancePlan>> plans(@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		return ok(service.plans(dataScopes.resolveQueryOrganizations(authentication, organizationId)));
	}

	@PostMapping("/preventive-plans")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<PreventiveMaintenancePlan> createPlan(@RequestBody PlanCommand command, Authentication authentication) {
		dataScopes.requireOrganization(authentication, service.assetOrganization(command.assetId()));
		return ok(service.createPlan(command));
	}

	@PostMapping("/preventive-plans/{id}/complete")
	@PreAuthorize("hasAuthority('facility:asset:manage')")
	public ResultData<PreventiveMaintenancePlan> completePlan(
			@PathVariable String id,
			@RequestBody NoteCommand command,
			Authentication authentication) {
		dataScopes.requireOrganization(authentication, service.planOrganization(id));
		return ok(service.completePlan(id, command.note(), authentication.getName()));
	}

	@GetMapping("/inventory-transactions")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:material:view','facility:material:manage')")
	public ResultData<List<InventoryTransaction>> transactions(@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		return ok(service.transactions(dataScopes.resolveQueryOrganizations(authentication, organizationId)));
	}

	@PostMapping("/inventory-transactions")
	@PreAuthorize("hasAuthority('facility:material:manage')")
	public ResultData<InventoryTransaction> adjustInventory(
			@RequestBody InventoryCommand command,
			Authentication authentication) {
		dataScopes.requireOrganization(authentication, service.materialOrganization(command.materialId()));
		return ok(service.adjustInventory(command, authentication.getName()));
	}

	@GetMapping("/safety-incidents")
	@PreAuthorize("hasAuthority('facility:asset:view')")
	public ResultData<List<FacilitySafetyIncident>> incidents(@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		return ok(service.incidents(dataScopes.resolveQueryOrganizations(authentication, organizationId)));
	}

	@PostMapping("/safety-incidents")
	@PreAuthorize("hasAuthority('facility:safety:manage')")
	public ResultData<FacilitySafetyIncident> reportIncident(
			@RequestBody IncidentCommand command,
			Authentication authentication) {
		dataScopes.requireOrganization(authentication, command.organizationId());
		if ((org.springframework.util.StringUtils.hasText(command.assetId())
				&& !command.organizationId().equals(service.assetOrganization(command.assetId())))
				|| (org.springframework.util.StringUtils.hasText(command.repairOrderId())
				&& !command.organizationId().equals(maintenance.orderOrganization(command.repairOrderId())))) {
			throw new IllegalArgumentException("关联记录不属于当前机构");
		}
		return ok(service.reportIncident(command, authentication.getName()));
	}

	@PostMapping("/safety-incidents/{id}/close")
	@PreAuthorize("hasAuthority('facility:safety:manage')")
	public ResultData<FacilitySafetyIncident> closeIncident(
			@PathVariable String id,
			@RequestBody NoteCommand command,
			Authentication authentication) {
		dataScopes.requireOrganization(authentication, service.incidentOrganization(id));
		return ok(service.closeIncident(id, command.note(), authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}

	public record NoteCommand(String note) {
	}
}
