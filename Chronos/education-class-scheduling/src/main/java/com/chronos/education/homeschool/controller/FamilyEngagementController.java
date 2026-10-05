package com.chronos.education.homeschool.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.homeschool.dto.FamilyEngagementDtos.*;
import com.chronos.education.homeschool.service.FamilyEngagementService;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class FamilyEngagementController {
	private final FamilyEngagementService service;

	public FamilyEngagementController(FamilyEngagementService service) {
		this.service = service;
	}

	@PostMapping("/portal/education/family/feedback")
	@PreAuthorize("isAuthenticated()")
	public ResultData<FeedbackResponse> submit(@RequestBody FeedbackCommand command, Authentication auth) {
		return ok(service.submit(command, auth.getName()));
	}

	@GetMapping("/portal/education/family/feedback")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<FeedbackResponse>> parentFeedbacks(Authentication auth) {
		return ok(service.parentFeedbacks(auth.getName()));
	}

	@PostMapping("/portal/education/family/feedback/{id}/confirm")
	@PreAuthorize("isAuthenticated()")
	public ResultData<FeedbackResponse> confirm(@PathVariable String id, Authentication auth) {
		return ok(service.parentConfirm(id, false, auth.getName()));
	}

	@PostMapping("/portal/education/family/feedback/{id}/reopen")
	@PreAuthorize("isAuthenticated()")
	public ResultData<FeedbackResponse> reopen(@PathVariable String id, Authentication auth) {
		return ok(service.parentConfirm(id, true, auth.getName()));
	}

	@GetMapping("/admin/education/home-school/feedback")
	@PreAuthorize("hasAuthority('education:home-school:feedback:view')")
	public ResultData<List<FeedbackResponse>> staffFeedbacks(Authentication auth) {
		return ok(service.staffFeedbacks(auth.getName()));
	}

	@PostMapping("/admin/education/home-school/feedback/{id}/transition")
	@PreAuthorize("hasAuthority('education:home-school:feedback:update')")
	public ResultData<FeedbackResponse> transition(@PathVariable String id, @RequestBody FeedbackActionCommand command,
			Authentication auth) {
		return ok(service.transition(id, command, auth.getName()));
	}

	@PostMapping("/admin/education/home-school/feedback/{id}/reply")
	@PreAuthorize("hasAuthority('education:home-school:feedback:update')")
	public ResultData<FeedbackResponse> reply(@PathVariable String id,
			@RequestBody(required = false) Map<String, String> payload, Authentication auth) {
		return ok(service.reply(id, payload == null ? null : payload.get("reply"), auth.getName()));
	}

	@PostMapping("/admin/education/home-school/communications")
	@PreAuthorize("hasAuthority('education:home-school:communication:create')")
	public ResultData<CommunicationResponse> record(@RequestBody CommunicationCommand command, Authentication auth) {
		return ok(service.record(command, auth.getName()));
	}

	@GetMapping("/admin/education/home-school/communications")
	@PreAuthorize("hasAuthority('education:home-school:communication:view')")
	public ResultData<List<CommunicationResponse>> staffCommunications(Authentication auth) {
		return ok(service.listCommunications(auth.getName()));
	}

	@GetMapping("/portal/education/family/communications")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<CommunicationResponse>> familyCommunications(Authentication auth) {
		return ok(service.listCommunications(auth.getName()));
	}

	@GetMapping("/admin/education/home-school/communications/export")
	@PreAuthorize("hasAuthority('education:home-school:communication:export')")
	public ResponseEntity<ByteArrayResource> export(Authentication auth) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename("home-school-communications.csv").build().toString())
				.contentType(MediaType.parseMediaType("text/csv"))
				.body(new ByteArrayResource(service.exportCommunications(auth.getName())));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
