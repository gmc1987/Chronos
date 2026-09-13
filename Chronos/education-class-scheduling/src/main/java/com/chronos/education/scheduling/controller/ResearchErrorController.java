package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.dto.ResearchErrorDtos.*;
import com.chronos.education.scheduling.service.ResearchErrorService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/teaching-center")
public class ResearchErrorController {
	private final ResearchErrorService service;
	public ResearchErrorController(ResearchErrorService service) { this.service=service; }
	@PostMapping("/research-groups")
	public ResultData<?> group(@Valid @RequestBody GroupRequest r, Authentication a) { return ok(service.createGroup(r,a)); }
	@PostMapping("/research-groups/{id}/members")
	public ResultData<?> member(@PathVariable String id,@Valid @RequestBody MemberRequest r,Authentication a) { return ok(service.addMember(id,r,a)); }
	@PostMapping("/research-groups/{id}/activities")
	public ResultData<?> activity(@PathVariable String id,@Valid @RequestBody ActivityRequest r,Authentication a) { return ok(service.createActivity(id,r,a)); }
	@PostMapping("/research-activities/{id}/members")
	public ResultData<?> activityMember(@PathVariable String id,@Valid @RequestBody MemberRequest r,Authentication a) { return ok(service.inviteActivityMember(id,r,a)); }
	@PostMapping("/research-activities/{id}/attendance")
	public ResultData<?> attendance(@PathVariable String id,@Valid @RequestBody AttendanceRequest r,Authentication a) { return ok(service.attendance(id,r,a)); }
	@PostMapping("/research-activities/{id}/materials")
	public ResultData<?> material(@PathVariable String id,@Valid @RequestBody MaterialRequest r,Authentication a) { return ok(service.addMaterial(id,r,a)); }
	@PostMapping("/research-activities/{id}/results")
	public ResultData<?> result(@PathVariable String id,@Valid @RequestBody ResultRequest r,Authentication a) { return ok(service.addResult(id,r,a)); }
	@PostMapping("/research-results/{id}/submit")
	public ResultData<?> submit(@PathVariable String id,Authentication a) { return ok(service.submitResult(id,a)); }
	@PostMapping("/error-books/manual")
	public ResultData<?> manual(@Valid @RequestBody ErrorManualRequest r,Authentication a) { return ok(service.recordManual(r,a)); }
	@PostMapping("/error-books/wrong-answer-confirmed")
	public ResultData<?> confirmed(@Valid @RequestBody WrongAnswerConfirmed r,Authentication a) { return ok(service.onWrongAnswerConfirmed(r,a)); }
	@PostMapping("/error-items/{id}/mastery")
	public ResultData<?> mastery(@PathVariable String id,@Valid @RequestBody MasteryRequest r,Authentication a) { return ok(service.mastery(id,r,a)); }
	private <T> ResultData<T> ok(T value) { return ResultData.<T>builder().code("200").msg("success").data(value).build(); }
}
