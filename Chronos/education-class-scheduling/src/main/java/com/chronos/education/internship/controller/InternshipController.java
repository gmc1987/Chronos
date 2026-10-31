package com.chronos.education.internship.controller;

import static com.chronos.education.internship.dto.InternshipDtos.*;

import com.chronos.commons.model.ResultData;
import com.chronos.education.internship.model.InternshipActivity;
import com.chronos.education.internship.model.InternshipAssessment;
import com.chronos.education.internship.model.InternshipCompany;
import com.chronos.education.internship.model.InternshipIncident;
import com.chronos.education.internship.model.InternshipPlacement;
import com.chronos.education.internship.model.InternshipPosition;
import com.chronos.education.internship.service.InternshipService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 高职实习实训的人工业务入口，审批和安全事件结案由独立权限控制。 */
@RestController
@RequestMapping("/admin/education/internships")
public class InternshipController {
	private final InternshipService service;

	public InternshipController(InternshipService service) {
		this.service = service;
	}

	@GetMapping("/companies")
	@PreAuthorize("hasAuthority('education:internship:view')")
	public ResultData<List<InternshipCompany>> companies(
			@RequestParam String schoolId,
			Authentication authentication) {
		return ok(service.companies(schoolId, authentication.getName()));
	}

	@PostMapping("/companies")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipCompany> createCompany(
			@RequestBody CompanyCommand command,
			Authentication authentication) {
		return ok(service.createCompany(command, authentication.getName()));
	}

	@PostMapping("/companies/{id}/submit")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipCompany> submitCompany(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.submitCompany(id, authentication.getName()));
	}

	@PostMapping("/companies/{id}/decision")
	@PreAuthorize("hasAuthority('education:internship:approve')")
	public ResultData<InternshipCompany> decideCompany(
			@PathVariable String id,
			@RequestBody DecisionCommand command,
			Authentication authentication) {
		return ok(service.decideCompany(id, command, authentication.getName()));
	}

	@GetMapping("/positions")
	@PreAuthorize("hasAuthority('education:internship:view')")
	public ResultData<List<InternshipPosition>> positions(
			@RequestParam String schoolId,
			Authentication authentication) {
		return ok(service.positions(schoolId, authentication.getName()));
	}

	@PostMapping("/positions")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipPosition> createPosition(
			@RequestBody PositionCommand command,
			Authentication authentication) {
		return ok(service.createPosition(command, authentication.getName()));
	}

	@GetMapping("/placements")
	@PreAuthorize("hasAuthority('education:internship:view')")
	public ResultData<List<InternshipPlacement>> placements(
			@RequestParam String schoolId,
			@RequestParam String termCode,
			Authentication authentication) {
		return ok(service.placements(schoolId, termCode, authentication.getName()));
	}

	@PostMapping("/placements")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipPlacement> createPlacement(
			@RequestBody PlacementCommand command,
			Authentication authentication) {
		return ok(service.createPlacement(command, authentication.getName()));
	}

	@PostMapping("/placements/{id}/submit")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipPlacement> submitPlacement(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.submitPlacement(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/decision")
	@PreAuthorize("hasAuthority('education:internship:approve')")
	public ResultData<InternshipPlacement> decidePlacement(
			@PathVariable String id,
			@RequestBody DecisionCommand command,
			Authentication authentication) {
		return ok(service.decidePlacement(id, command, authentication.getName()));
	}

	@PostMapping("/placements/{id}/activate")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipPlacement> activatePlacement(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.activatePlacement(id, authentication.getName()));
	}

	@GetMapping("/placements/{id}/activities")
	@PreAuthorize("hasAuthority('education:internship:view')")
	public ResultData<List<InternshipActivity>> activities(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.activities(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/activities")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipActivity> addActivity(
			@PathVariable String id,
			@RequestBody ActivityCommand command,
			Authentication authentication) {
		return ok(service.addActivity(id, command, authentication.getName()));
	}

	@GetMapping("/placements/{id}/assessment")
	@PreAuthorize("hasAuthority('education:internship:view')")
	public ResultData<InternshipAssessment> assessment(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.assessment(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/assessment")
	@PreAuthorize("hasAuthority('education:internship:manage')")
	public ResultData<InternshipAssessment> submitAssessment(
			@PathVariable String id,
			@RequestBody AssessmentCommand command,
			Authentication authentication) {
		return ok(service.submitAssessment(id, command, authentication.getName()));
	}

	@PostMapping("/assessments/{id}/decision")
	@PreAuthorize("hasAuthority('education:internship:approve')")
	public ResultData<InternshipAssessment> decideAssessment(
			@PathVariable String id,
			@RequestBody DecisionCommand command,
			Authentication authentication) {
		return ok(service.decideAssessment(id, command, authentication.getName()));
	}

	@GetMapping("/placements/{id}/incidents")
	@PreAuthorize("hasAuthority('education:internship:view')")
	public ResultData<List<InternshipIncident>> incidents(
			@PathVariable String id,
			Authentication authentication) {
		return ok(service.incidents(id, authentication.getName()));
	}

	@PostMapping("/placements/{id}/incidents")
	@PreAuthorize("hasAuthority('education:internship:safety')")
	public ResultData<InternshipIncident> reportIncident(
			@PathVariable String id,
			@RequestBody IncidentCommand command,
			Authentication authentication) {
		return ok(service.reportIncident(id, command, authentication.getName()));
	}

	@PostMapping("/incidents/{id}/close")
	@PreAuthorize("hasAuthority('education:internship:safety')")
	public ResultData<InternshipIncident> closeIncident(
			@PathVariable String id,
			@RequestBody ClosureCommand command,
			Authentication authentication) {
		return ok(service.closeIncident(id, command, authentication.getName()));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
