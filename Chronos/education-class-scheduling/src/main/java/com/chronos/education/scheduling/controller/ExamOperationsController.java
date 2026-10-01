package com.chronos.education.scheduling.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.ExamAccommodation;
import com.chronos.education.scheduling.model.ExamAdmissionTicket;
import com.chronos.education.scheduling.model.ExamIncident;
import com.chronos.education.scheduling.model.ExamIncidentAction;
import com.chronos.education.scheduling.model.ExamMaterialHandover;
import com.chronos.education.scheduling.model.ExamMaterialLedger;
import com.chronos.education.scheduling.model.ExamOperationsCommands;
import com.chronos.education.scheduling.model.ExamRegistration;
import com.chronos.education.scheduling.service.ExamOperationsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ExamOperationsController {
	private final ExamOperationsService service;

	@PostMapping("/portal/education/exam/registrations")
	@PreAuthorize("isAuthenticated()")
	public ResultData<ExamRegistration> register(
			@RequestBody ExamOperationsCommands.Registration command,
			Authentication authentication) {
		return ok(service.register(command, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/sessions/{sessionId}/registrations")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:registration:view','education:exam:registration:manage')")
	public ResultData<List<ExamRegistration>> registrations(@PathVariable String sessionId) {
		return ok(service.registrations(sessionId));
	}

	@PostMapping("/admin/education/exam/accommodations")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:accommodation:manage')")
	public ResultData<ExamAccommodation> requestAccommodation(
			@RequestBody ExamOperationsCommands.Accommodation command,
			Authentication authentication) {
		return ok(service.requestAccommodation(command, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/registrations/{registrationId}/accommodations")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:registration:view','education:exam:accommodation:manage')")
	public ResultData<List<ExamAccommodation>> accommodations(@PathVariable String registrationId) {
		return ok(service.accommodations(registrationId));
	}

	@PostMapping("/admin/education/exam/accommodations/{id}/decision")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:accommodation:manage')")
	public ResultData<ExamAccommodation> decideAccommodation(
			@PathVariable String id,
			@RequestBody ExamOperationsCommands.AccommodationDecision command,
			Authentication authentication) {
		return ok(service.decideAccommodation(id, command, authentication.getName()));
	}

	@PostMapping("/admin/education/exam/plans/{planId}/tickets/generate")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:ticket:manage')")
	public ResultData<List<ExamAdmissionTicket>> generateTickets(
			@PathVariable String planId,
			Authentication authentication) {
		return ok(service.generateTickets(planId, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/candidates/{candidateId}/tickets")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:ticket:view','education:exam:ticket:manage')")
	public ResultData<List<ExamAdmissionTicket>> tickets(@PathVariable String candidateId) {
		return ok(service.tickets(candidateId));
	}

	@PostMapping("/admin/education/exam/materials")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:material:manage')")
	public ResultData<ExamMaterialLedger> createLedger(
			@RequestBody ExamOperationsCommands.Material command,
			Authentication authentication) {
		return ok(service.createLedger(command, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/sessions/{sessionId}/materials")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:material:manage')")
	public ResultData<List<ExamMaterialLedger>> ledgers(@PathVariable String sessionId) {
		return ok(service.ledgers(sessionId));
	}

	@PostMapping("/admin/education/exam/materials/{ledgerId}/handovers")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:material:manage')")
	public ResultData<ExamMaterialLedger> handover(
			@PathVariable String ledgerId,
			@RequestBody ExamOperationsCommands.Handover command,
			Authentication authentication) {
		ExamOperationsCommands.Handover normalized = new ExamOperationsCommands.Handover(
				ledgerId, command.receivedBy(), command.quantity(), command.differenceReason());
		return ok(service.handover(normalized, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/materials/{ledgerId}/handovers")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:material:manage')")
	public ResultData<List<ExamMaterialHandover>> handovers(@PathVariable String ledgerId) {
		return ok(service.handovers(ledgerId));
	}

	@PostMapping("/admin/education/exam/incidents")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:incident:manage')")
	public ResultData<ExamIncident> reportIncident(
			@RequestBody ExamOperationsCommands.Incident command,
			Authentication authentication) {
		return ok(service.reportIncident(command, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/sessions/{sessionId}/incidents")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:incident:view','education:exam:incident:manage')")
	public ResultData<List<ExamIncident>> incidents(@PathVariable String sessionId) {
		return ok(service.incidents(sessionId));
	}

	@PostMapping("/admin/education/exam/incidents/{incidentId}/actions")
	@PreAuthorize("@iamAuthorization.has(authentication,'education:exam:incident:manage')")
	public ResultData<ExamIncidentAction> incidentAction(
			@PathVariable String incidentId,
			@RequestBody ExamOperationsCommands.IncidentAction command,
			Authentication authentication) {
		return ok(service.actOnIncident(incidentId, command, authentication.getName()));
	}

	@GetMapping("/admin/education/exam/incidents/{incidentId}/actions")
	@PreAuthorize("@iamAuthorization.any(authentication,'education:exam:incident:view','education:exam:incident:manage')")
	public ResultData<List<ExamIncidentAction>> incidentActions(@PathVariable String incidentId) {
		return ok(service.incidentActions(incidentId));
	}

	private <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("ok").data(data).build();
	}
}
