package com.chronos.facility.controller;

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
import org.springframework.security.core.Authentication;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.facility.model.dto.FacilityDtos.ActionCommand;
import com.chronos.facility.model.dto.FacilityDtos.DispatchCommand;
import com.chronos.facility.model.dto.FacilityDtos.LedgerView;
import com.chronos.facility.model.dto.FacilityDtos.MaterialCommand;
import com.chronos.facility.model.dto.FacilityDtos.MaterialRequestView;
import com.chronos.facility.model.dto.FacilityDtos.MaterialView;
import com.chronos.facility.model.dto.FacilityDtos.OrganizationOption;
import com.chronos.facility.model.dto.FacilityDtos.RepairOrderView;
import com.chronos.facility.model.dto.FacilityDtos.ReviewMaterialRequest;
import com.chronos.facility.model.dto.FacilityDtos.StockCommand;
import com.chronos.facility.model.dto.FacilityDtos.TechnicianCommand;
import com.chronos.facility.model.dto.FacilityDtos.TechnicianView;
import com.chronos.facility.service.FacilityMaintenanceService;
import com.chronos.security.DirectoryDataScopeGuard;

@RestController
@RequestMapping("/admin/facility")
public class AdminFacilityMaintenanceController {
	private final FacilityMaintenanceService service;
	private final DirectoryDataScopeGuard dataScopes;

	public AdminFacilityMaintenanceController(FacilityMaintenanceService service, DirectoryDataScopeGuard dataScopes) {
		this.service = service;
		this.dataScopes = dataScopes;
	}

	@GetMapping("/repair-orders")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:view','facility:repair:manage')")
	public ResultData<PageView<RepairOrderView>> orders(
			@RequestParam(required = false) String organizationId,
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication authentication) {
		return ok(PageView.from(service.adminOrders(queryOrganizations(authentication, organizationId), keyword, status, page, size)));
	}

	@GetMapping("/repair-orders/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:view','facility:repair:manage')")
	public ResultData<RepairOrderView> detail(@PathVariable String id, Principal principal) {
		dataScopes.requireOrganization(auth(principal), service.orderOrganization(id));
		return ok(service.detail(id, principal.getName(), true));
	}

	@PostMapping("/repair-orders/{id}/dispatch")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:dispatch','facility:repair:manage')")
	public ResultData<RepairOrderView> dispatch(
			@PathVariable String id,
			@RequestBody DispatchCommand command,
			Principal principal) {
		dataScopes.requireOrganization(auth(principal), service.orderOrganization(id));
		return ok(service.dispatch(id, command, principal.getName()));
	}

	@PostMapping("/repair-orders/{id}/auto-dispatch")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:dispatch','facility:repair:manage')")
	public ResultData<RepairOrderView> autoDispatch(@PathVariable String id, Principal principal) {
		dataScopes.requireOrganization(auth(principal), service.orderOrganization(id));
		return ok(service.retryAutoDispatch(id, principal.getName()));
	}

	@PostMapping("/repair-orders/{id}/cancel")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:repair:manage')")
	public ResultData<RepairOrderView> cancel(
			@PathVariable String id,
			@RequestBody ActionCommand command,
			Principal principal) {
		dataScopes.requireOrganization(auth(principal), service.orderOrganization(id));
		return ok(service.cancel(id, command, principal.getName(), true));
	}

	@GetMapping("/technicians")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:technician:view','facility:technician:manage')")
	public ResultData<List<TechnicianView>> technicians(
			@RequestParam(required = false) String organizationId,
			Authentication authentication) {
		return ok(service.technicians(queryOrganizations(authentication, organizationId)));
	}

	@GetMapping("/organization-options")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:technician:view','facility:technician:manage')")
	public ResultData<List<OrganizationOption>> organizationOptions(Authentication authentication) {
		return ok(service.organizationOptions(dataScopes.visibleOrganizationIds(authentication)));
	}

	@PostMapping("/technicians")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:technician:manage')")
	public ResultData<TechnicianView> createTechnician(
			@RequestBody TechnicianCommand command, Authentication authentication) {
		validateTechnicianScope(authentication, command);
		return ok(service.saveTechnician(null, command));
	}

	@PutMapping("/technicians/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:technician:manage')")
	public ResultData<TechnicianView> updateTechnician(
			@PathVariable String id,
			@RequestBody TechnicianCommand command,
			Authentication authentication) {
		validateTechnicianScope(authentication, command);
		return ok(service.saveTechnician(id, command));
	}

	@GetMapping("/materials")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:material:view','facility:material:manage')")
	public ResultData<PageView<MaterialView>> materials(
			@RequestParam(required = false) String organizationId,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, Authentication authentication) {
		return ok(PageView.from(service.materials(queryOrganizations(authentication, organizationId), keyword, page, size)));
	}

	@PostMapping("/materials")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:material:manage')")
	public ResultData<MaterialView> createMaterial(@RequestBody MaterialCommand command, Authentication authentication) {
		return ok(service.saveMaterial(null, command, resolveOrganization(authentication, command.organizationId())));
	}

	@PutMapping("/materials/{id}")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:material:manage')")
	public ResultData<MaterialView> updateMaterial(
			@PathVariable String id,
			@RequestBody MaterialCommand command, Authentication authentication) {
		String organizationId = service.materialOrganization(id);
		dataScopes.requireOrganization(authentication, organizationId);
		return ok(service.saveMaterial(id, command, organizationId));
	}

	@PostMapping("/materials/{id}/restock")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:material:manage')")
	public ResultData<MaterialView> restock(
			@PathVariable String id,
			@RequestBody StockCommand command,
			Principal principal) {
		dataScopes.requireOrganization(auth(principal), service.materialOrganization(id));
		return ok(service.restock(id, command, principal.getName()));
	}

	@GetMapping("/material-requests")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:material-request:view','facility:material-request:manage')")
	public ResultData<PageView<MaterialRequestView>> materialRequests(
			@RequestParam(required = false) String organizationId,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication authentication) {
		return ok(PageView.from(service.materialRequests(
				queryOrganizations(authentication, organizationId), status, page, size)));
	}

	@PostMapping("/material-requests/{id}/review")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:material-request:manage')")
	public ResultData<MaterialRequestView> review(
			@PathVariable String id,
			@RequestBody ReviewMaterialRequest command,
			Principal principal) {
		dataScopes.requireOrganization(auth(principal), service.materialRequestOrganization(id));
		return ok(service.reviewMaterialRequest(id, command, principal.getName()));
	}

	@PostMapping("/material-requests/{id}/issue")
	@PreAuthorize("@iamAuthorization.has(authentication,'facility:material-request:manage')")
	public ResultData<MaterialRequestView> issue(@PathVariable String id, Principal principal) {
		dataScopes.requireOrganization(auth(principal), service.materialRequestOrganization(id));
		return ok(service.issueMaterialRequest(id, principal.getName()));
	}

	@GetMapping("/ledger")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:ledger:view','facility:repair:manage')")
	public ResultData<PageView<LedgerView>> ledger(
			@RequestParam(required = false) String organizationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Authentication authentication) {
		return ok(PageView.from(service.ledger(
				queryOrganizations(authentication, organizationId), page, size)));
	}

	private Authentication auth(Principal principal) {
		if (principal instanceof Authentication authentication) return authentication;
		return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal.getName(), "", List.of());
	}

	private java.util.Set<String> queryOrganizations(Authentication authentication, String organizationId) {
		return dataScopes.resolveQueryOrganizations(authentication, organizationId);
	}

	private String resolveOrganization(Authentication authentication, String requested) {
		return dataScopes.resolveOrganization(authentication, requested);
	}

	private void validateTechnicianScope(Authentication authentication, TechnicianCommand command) {
		dataScopes.requireEmployee(authentication, command.employeeId());
		var values = command.responsibilityOrganizationIds() == null
				? java.util.Set.<String>of()
				: java.util.Arrays.stream(command.responsibilityOrganizationIds().split(","))
						.map(String::trim).filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toSet());
		if (values.contains("ALL")) {
			dataScopes.requireFullAccess(authentication);
			return;
		}
		if (values.isEmpty()) throw new IllegalArgumentException("请选择维修人员责任机构");
		values.forEach(value -> dataScopes.requireOrganization(authentication, value));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
