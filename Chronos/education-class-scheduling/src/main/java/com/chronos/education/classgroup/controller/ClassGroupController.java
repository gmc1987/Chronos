package com.chronos.education.classgroup.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.education.classgroup.dto.ClassGroupDtos.*;
import com.chronos.education.classgroup.service.ClassGroupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClassGroupController {
	private final ClassGroupService service;
	public ClassGroupController(ClassGroupService service) { this.service = service; }

	@GetMapping("/admin/education/home-school/class-groups")
	@PreAuthorize("hasAuthority('education:home-school:group:view')")
	public ResultData<PageView<GroupResponse>> page(Authentication a, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) { return ok(service.page(a.getName(), page, size)); }

	@PostMapping("/admin/education/home-school/class-groups")
	@PreAuthorize("hasAuthority('education:home-school:group:create')")
	public ResultData<GroupResponse> create(@RequestBody GroupCreateRequest request, Authentication a,
			@RequestHeader(value = "Idempotency-Key", required = false) String key) {
		return ok(service.create(a.getName(), request.classId(), request.name(), key));
	}

	@PostMapping("/admin/education/home-school/class-groups/{id}/status")
	@PreAuthorize("hasAuthority('education:home-school:group:update')")
	public ResultData<GroupResponse> status(@PathVariable String id, @RequestBody StatusCommand command, Authentication a) {
		return ok(service.changeStatus(a.getName(), id, command));
	}
	@PostMapping("/admin/education/home-school/class-groups/{id}/sync")
	@PreAuthorize("hasAuthority('education:home-school:group:manage')")
	public ResultData<GroupResponse> sync(@PathVariable String id, @RequestBody(required = false) SyncCommand command, Authentication a) {
		return ok(service.sync(a.getName(), id, command));
	}
	@GetMapping("/admin/education/home-school/class-groups/{id}/members")
	@PreAuthorize("hasAuthority('education:home-school:group:view')")
	public ResultData<PageView<MemberResponse>> members(@PathVariable String id, Authentication a,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ok(service.members(a.getName(), id, page, size));
	}
	@PostMapping("/admin/education/home-school/class-groups/{id}/members")
	@PreAuthorize("hasAuthority('education:home-school:group:manage')")
	public ResultData<MemberResponse> add(@PathVariable String id, @RequestBody MemberCommand command, Authentication a) {
		return ok(service.addMember(a.getName(), id, command));
	}
	@DeleteMapping("/admin/education/home-school/class-groups/{id}/members/{memberId}")
	@PreAuthorize("hasAuthority('education:home-school:group:manage')")
	public ResultData<MemberResponse> remove(@PathVariable String id, @PathVariable String memberId, Authentication a) {
		return ok(service.removeMember(a.getName(), id, memberId));
	}
	@GetMapping("/admin/education/home-school/class-groups/{id}/audit")
	@PreAuthorize("hasAuthority('education:home-school:group:view')")
	public ResultData<PageView<AuditResponse>> audit(@PathVariable String id, Authentication a,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return ok(service.audit(a.getName(), id, page, size));
	}

	public record GroupCreateRequest(String classId, String name) {}
	private <T> ResultData<T> ok(T value) { return ResultData.<T>builder().code("200").msg("ok").data(value).build(); }
}
