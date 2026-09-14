package com.chronos.education.scheduling.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.CombinedOfferingView;
import com.chronos.education.scheduling.service.CombinedOfferingService;
import com.chronos.education.scheduling.service.EducationDataScopeService;

@RestController
@RequestMapping("/admin/education/course-offerings/{offeringId}/combined-classes")
public class CombinedOfferingController {
	private final CombinedOfferingService service;
	private final EducationDataScopeService scopes;

	public CombinedOfferingController(
			CombinedOfferingService service,
			EducationDataScopeService scopes) {
		this.service = service;
		this.scopes = scopes;
	}

	@GetMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:view','education:scheduling:manage')")
	public ResultData<CombinedOfferingView> details(
			@PathVariable String offeringId,
			Authentication authentication) {
		scopes.assertOfferingAccess(scopes.resolve(authentication.getName()), offeringId);
		return ok(service.details(offeringId));
	}

	@PutMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<CombinedOfferingView> configure(
			@PathVariable String offeringId,
			@RequestBody SourceClassesCommand command,
			Authentication authentication) {
		var scope = scopes.resolve(authentication.getName());
		scopes.assertOfferingAccess(scope, offeringId);
		if (command == null || command.administrativeClassIds() == null) {
			throw new IllegalArgumentException("请选择来源行政班");
		}
		for (String classId : command.administrativeClassIds()) {
			scopes.assertClassAccess(scope, classId);
		}
		return ok(service.configure(offeringId, command.administrativeClassIds()));
	}

	@PostMapping("/sync")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:update','education:scheduling:manage')")
	public ResultData<CombinedOfferingView> sync(
			@PathVariable String offeringId,
			Authentication authentication) {
		var scope = scopes.resolve(authentication.getName());
		scopes.assertOfferingAccess(scope, offeringId);
		for (String classId : service.details(offeringId).administrativeClassIds()) {
			scopes.assertClassAccess(scope, classId);
		}
		return ok(service.sync(offeringId));
	}

	@DeleteMapping
	@PreAuthorize("@iamAuthorization.any(authentication,'education:scheduling:delete','education:scheduling:manage')")
	public ResultData<Void> delete(
			@PathVariable String offeringId,
			Authentication authentication) {
		scopes.assertOfferingAccess(scopes.resolve(authentication.getName()), offeringId);
		service.delete(offeringId);
		return ResultData.<Void>builder().code("200").msg("ok").build();
	}

	private ResultData<CombinedOfferingView> ok(CombinedOfferingView value) {
		return ResultData.<CombinedOfferingView>builder()
				.code("200")
				.msg("ok")
				.data(value)
				.build();
	}

	public record SourceClassesCommand(List<String> administrativeClassIds) {
	}
}
