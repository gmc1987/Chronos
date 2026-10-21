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

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.facility.model.dto.FacilityDtos.ActionCommand;
import com.chronos.facility.model.dto.FacilityDtos.CloseCommand;
import com.chronos.facility.model.dto.FacilityDtos.CompleteCommand;
import com.chronos.facility.model.dto.FacilityDtos.CreateMaterialRequest;
import com.chronos.facility.model.dto.FacilityDtos.CreateRepairOrder;
import com.chronos.facility.model.dto.FacilityDtos.MaterialRequestView;
import com.chronos.facility.model.dto.FacilityDtos.MaterialView;
import com.chronos.facility.model.dto.FacilityDtos.RepairOrderView;
import com.chronos.facility.service.FacilityMaintenanceService;

@RestController
@RequestMapping("/portal/facility")
public class FacilityMaintenanceController {
	private final FacilityMaintenanceService service;

	public FacilityMaintenanceController(FacilityMaintenanceService service) {
		this.service = service;
	}

	@PostMapping("/repair-orders")
	@PreAuthorize("isAuthenticated()")
	public ResultData<RepairOrderView> create(@RequestBody CreateRepairOrder command, Principal principal) {
		return ok(service.create(command, principal.getName()));
	}

	@GetMapping("/repair-orders")
	@PreAuthorize("isAuthenticated()")
	public ResultData<PageView<RepairOrderView>> myOrders(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Principal principal) {
		return ok(PageView.from(service.myOrders(principal.getName(), page, size)));
	}

	@GetMapping("/repair-orders/{id}")
	@PreAuthorize("isAuthenticated()")
	public ResultData<RepairOrderView> detail(@PathVariable String id, Principal principal) {
		return ok(service.detail(id, principal.getName(), false));
	}

	@PostMapping("/repair-orders/{id}/close")
	@PreAuthorize("isAuthenticated()")
	public ResultData<RepairOrderView> close(
			@PathVariable String id,
			@RequestBody CloseCommand command,
			Principal principal) {
		return ok(service.close(id, command, principal.getName()));
	}

	@PostMapping("/repair-orders/{id}/cancel")
	@PreAuthorize("isAuthenticated()")
	public ResultData<RepairOrderView> cancel(
			@PathVariable String id,
			@RequestBody ActionCommand command,
			Principal principal) {
		return ok(service.cancel(id, command, principal.getName(), false));
	}

	@GetMapping("/assigned-repair-orders")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:work','facility:repair:manage')")
	public ResultData<PageView<RepairOrderView>> assigned(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			Principal principal) {
		return ok(PageView.from(service.assignedOrders(principal.getName(), page, size)));
	}

	@PostMapping("/assigned-repair-orders/{id}/accept")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:work','facility:repair:manage')")
	public ResultData<RepairOrderView> accept(@PathVariable String id, Principal principal) {
		return ok(service.accept(id, principal.getName()));
	}

	@PostMapping("/assigned-repair-orders/{id}/start")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:work','facility:repair:manage')")
	public ResultData<RepairOrderView> start(@PathVariable String id, Principal principal) {
		return ok(service.start(id, principal.getName()));
	}

	@PostMapping("/assigned-repair-orders/{id}/complete")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:work','facility:repair:manage')")
	public ResultData<RepairOrderView> complete(
			@PathVariable String id,
			@RequestBody CompleteCommand command,
			Principal principal) {
		return ok(service.complete(id, command, principal.getName()));
	}

	@PostMapping("/material-requests")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:work','facility:repair:manage')")
	public ResultData<MaterialRequestView> requestMaterials(
			@RequestBody CreateMaterialRequest command,
			Principal principal) {
		return ok(service.requestMaterials(command, principal.getName()));
	}

	@GetMapping("/materials")
	@PreAuthorize("@iamAuthorization.any(authentication,'facility:repair:work','facility:repair:manage')")
	public ResultData<PageView<MaterialView>> materials(
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size) {
		return ok(PageView.from(service.materials(keyword, page, size)));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
