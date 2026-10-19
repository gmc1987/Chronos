package com.chronos.message.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.message.service.impl.MobileDeviceRegistrationService;
import com.chronos.message.service.impl.MobileDeviceRegistrationService.DeviceView;
import com.chronos.message.service.impl.MobileDeviceRegistrationService.RegistrationCommand;

/** Authenticated mobile installation registration for APNs and FCM delivery providers. */
@RestController
public class MobileDeviceRegistrationController {
	private final MobileDeviceRegistrationService service;

	public MobileDeviceRegistrationController(MobileDeviceRegistrationService service) {
		this.service = service;
	}

	@GetMapping("/message/mobile-devices")
	public ResultData<List<DeviceView>> mine(Principal principal) {
		return ok(service.mine(principal.getName()));
	}

	@PutMapping("/message/mobile-devices/{installationId}")
	public ResultData<DeviceView> register(
			@PathVariable String installationId,
			@RequestBody RegistrationCommand command,
			Principal principal) {
		return ok(service.register(principal.getName(), installationId, command));
	}

	@DeleteMapping("/message/mobile-devices/{installationId}")
	public ResultData<Void> unregister(
			@PathVariable String installationId,
			@RequestParam String platform,
			Principal principal) {
		service.unregister(principal.getName(), platform, installationId);
		return ok(null);
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
