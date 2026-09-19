package com.chronos.education.homeschool.controller;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.chronos.commons.model.ResultData;
import com.chronos.education.homeschool.dto.HomeSchoolDtos.*;
import com.chronos.education.homeschool.service.HomeSchoolService;

@RestController
public class HomeSchoolController {
	private final HomeSchoolService service;
	public HomeSchoolController(HomeSchoolService service) { this.service = service; }

	@GetMapping("/admin/education/home-school/parent-bindings")
	@PreAuthorize("hasAuthority('education:home-school:parent:view')")
	public ResultData<List<ParentBindingResponse>> bindings() { return ok(service.listBindings()); }
	@PostMapping("/admin/education/home-school/parent-bindings")
	@PreAuthorize("hasAuthority('education:home-school:parent:create')")
	public ResultData<ParentBindingResponse> bind(@RequestBody ParentBindingCommand command) { return ok(service.bind(command)); }
	@PostMapping("/admin/education/home-school/parent-bindings/{id}/invalidate")
	@PreAuthorize("hasAuthority('education:home-school:parent:update')")
	public ResultData<ParentBindingResponse> invalidate(@PathVariable String id) { return ok(service.invalidate(id)); }

	@GetMapping("/admin/education/home-school/notices")
	@PreAuthorize("hasAuthority('education:home-school:notice:view')")
	public ResultData<List<NoticeResponse>> notices(Authentication authentication) { return ok(service.listNotices(authentication.getName())); }
	@PostMapping("/admin/education/home-school/notices")
	@PreAuthorize("hasAuthority('education:home-school:notice:create')")
	public ResultData<NoticeResponse> createNotice(@RequestBody NoticeCommand command, Authentication authentication) { return ok(service.createNotice(command, authentication.getName())); }
	@PostMapping("/admin/education/home-school/notices/{id}/publish")
	@PreAuthorize("hasAuthority('education:home-school:notice:update')")
	public ResultData<NoticeResponse> publish(@PathVariable String id, Authentication authentication) { return ok(service.publish(id, authentication.getName())); }
	@GetMapping("/admin/education/home-school/notices/{id}/receipts")
	@PreAuthorize("hasAuthority('education:home-school:notice:view')")
	public ResultData<List<NoticeTargetResponse>> receipts(@PathVariable String id, Authentication authentication) { return ok(service.receipts(id, authentication.getName())); }

	@GetMapping("/portal/education/family/children")
	@PreAuthorize("hasAuthority('education:home-school:parent:view')")
	public ResultData<List<ChildResponse>> children(Authentication authentication) { return ok(service.children(authentication.getName())); }
	@GetMapping("/portal/education/family/notices")
	@PreAuthorize("hasAuthority('education:home-school:notice:view')")
	public ResultData<List<FamilyNoticeResponse>> familyNotices(Authentication authentication) { return ok(service.familyNotices(authentication.getName())); }
	@PostMapping("/portal/education/family/notices/{id}/receipt")
	@PreAuthorize("hasAuthority('education:home-school:notice:update')")
	public ResultData<NoticeTargetResponse> receipt(@PathVariable String id, @RequestBody(required = false) ReceiptCommand command, Authentication authentication) { return ok(service.receipt(id, command, authentication.getName())); }

	private <T> ResultData<T> ok(T value) { return ResultData.<T>builder().code("200").msg("ok").data(value).build(); }
}
