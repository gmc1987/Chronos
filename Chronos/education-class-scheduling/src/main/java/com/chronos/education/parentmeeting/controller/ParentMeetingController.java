package com.chronos.education.parentmeeting.controller;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import com.chronos.commons.model.ResultData;
import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingView;
import com.chronos.education.parentmeeting.model.ParentMeetingCommands;
import com.chronos.education.parentmeeting.service.ParentMeetingService;

@RestController
public class ParentMeetingController {
	private final ParentMeetingService service;
	public ParentMeetingController(ParentMeetingService service) { this.service = service; }

	@PostMapping("/admin/education/parent-meetings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:parent-meeting:create','education:parent-meeting:manage')")
	public ResultData<MeetingView> create(@RequestBody @Valid ParentMeetingCommands.Create command,
			Authentication authentication) {
		return ok(service.create(command, authentication.getName()));
	}

	@GetMapping("/admin/education/parent-meetings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:parent-meeting:create','education:parent-meeting:manage')")
	public ResultData<List<MeetingView>> all(Authentication authentication) {
		boolean manage = authentication.getAuthorities().stream()
				.anyMatch(authority -> "education:parent-meeting:manage".equals(authority.getAuthority()));
		return ok(service.all(authentication.getName(), manage));
	}

	@PostMapping("/admin/education/parent-meetings/{id}/publish")
	@PreAuthorize("hasAuthority('education:parent-meeting:manage')")
	public ResultData<MeetingView> publish(@PathVariable String id, Authentication authentication) {
		return ok(service.publish(id, authentication.getName()));
	}

	@GetMapping("/portal/education/parent-meetings")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<MeetingView>> mine(Authentication authentication) {
		return ok(service.mine(authentication.getName()));
	}

	@PostMapping("/portal/education/parent-meetings/{id}/response")
	@PreAuthorize("isAuthenticated()")
	public ResultData<MeetingView> respond(@PathVariable String id,
			@RequestBody MeetingCommands.Response command, Authentication authentication) {
		return ok(service.respond(id, command, authentication.getName()));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}
}
