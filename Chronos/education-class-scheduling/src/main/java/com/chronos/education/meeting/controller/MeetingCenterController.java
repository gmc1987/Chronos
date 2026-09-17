package com.chronos.education.meeting.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingRoom;
import com.chronos.education.meeting.model.MeetingView;
import com.chronos.education.meeting.service.MeetingCenterService;
import com.chronos.security.IamAuthorization;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MeetingCenterController {
	private final MeetingCenterService service;
	private final IamAuthorization iamAuthorization;

	@GetMapping("/admin/education/meeting/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:room:view','education:meeting:room:manage','education:meeting:view','education:meeting:manage')")
	public ResultData<List<MeetingRoom>> rooms() {
		return ok(service.rooms());
	}

	@GetMapping("/admin/education/meeting/participant-options")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:view','education:meeting:create','education:meeting:update','education:meeting:manage','education:meeting:room:manage')")
	public ResultData<List<MeetingCommands.ParticipantOption>> participantOptions(
			@RequestParam(defaultValue = "") String keyword) {
		return ok(service.participantOptions(keyword));
	}

	@PostMapping("/admin/education/meeting/rooms")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:room:create','education:meeting:room:manage')")
	public ResultData<MeetingRoom> createRoom(@RequestBody MeetingCommands.Room command) {
		return ok(service.saveRoom(null, command));
	}

	@PutMapping("/admin/education/meeting/rooms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:room:update','education:meeting:room:manage')")
	public ResultData<MeetingRoom> updateRoom(
			@PathVariable String id,
			@RequestBody MeetingCommands.Room command) {
		return ok(service.saveRoom(id, command));
	}

	@DeleteMapping("/admin/education/meeting/rooms/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:room:delete','education:meeting:room:manage')")
	public ResultData<Void> deleteRoom(@PathVariable String id) {
		service.deleteRoom(id);
		return ok(null);
	}

	@GetMapping("/admin/education/meetings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:view','education:meeting:manage')")
	public ResultData<List<MeetingView>> meetings() {
		return ok(service.allMeetings());
	}

	@PostMapping("/admin/education/meetings")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:create','education:meeting:manage')")
	public ResultData<MeetingView> create(
			@RequestBody MeetingCommands.Save command,
			Authentication authentication) {
		return ok(service.saveMeeting(null, command, authentication.getName()));
	}

	@PutMapping("/admin/education/meetings/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:update','education:meeting:manage')")
	public ResultData<MeetingView> update(
			@PathVariable String id,
			@RequestBody MeetingCommands.Save command,
			Authentication authentication) {
		return ok(service.saveMeeting(id, command, authentication.getName()));
	}

	@DeleteMapping("/admin/education/meetings/{id}")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:delete','education:meeting:manage')")
	public ResultData<Void> delete(
			@PathVariable String id,
			Authentication authentication) {
		service.deleteMeeting(id, authentication.getName());
		return ok(null);
	}

	@PostMapping("/admin/education/meetings/{id}/publish")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:update','education:meeting:manage')")
	public ResultData<MeetingView> publish(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.publish(id, authentication.getName()));
	}

	@PostMapping("/admin/education/meetings/{id}/room-decision")
	@PreAuthorize("isAuthenticated()")
	public ResultData<MeetingView> decideRoom(
			@PathVariable String id,
			@RequestBody MeetingCommands.Decision command,
			Authentication authentication) {
		// 必须复用平台统一鉴权器，它同时识别原子权限、通配权限和超级管理员。
		boolean roomManager = iamAuthorization.any(
				authentication,
				"education:meeting:room:manage",
				"education:meeting:approve");
		return ok(service.decideRoom(
				id, command, authentication.getName(), roomManager));
	}

	@PostMapping("/admin/education/meetings/{id}/cancel")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:meeting:update','education:meeting:manage')")
	public ResultData<MeetingView> cancel(
			@PathVariable String id,
			@RequestBody MeetingCommands.Cancellation command,
			Authentication authentication) {
		return ok(service.cancel(id, command, authentication.getName()));
	}

	@GetMapping("/portal/education/meetings")
	@PreAuthorize("isAuthenticated()")
	public ResultData<List<MeetingView>> myMeetings(Authentication authentication) {
		return ok(service.myMeetings(authentication.getName()));
	}

	@PostMapping("/portal/education/meetings/{id}/response")
	@PreAuthorize("isAuthenticated()")
	public ResultData<MeetingView> respond(
			@PathVariable String id,
			@RequestBody MeetingCommands.Response command,
			Authentication authentication) {
		return ok(service.respond(id, command, authentication.getName()));
	}

	private <T> ResultData<T> ok(T value) {
		return ResultData.<T>builder().code("200").msg("ok").data(value).build();
	}

}
