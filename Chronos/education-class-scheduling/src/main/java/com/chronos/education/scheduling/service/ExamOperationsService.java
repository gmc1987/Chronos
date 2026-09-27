package com.chronos.education.scheduling.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ExamAccommodationRepository;
import com.chronos.education.scheduling.dao.ExamAdmissionTicketRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamIncidentActionRepository;
import com.chronos.education.scheduling.dao.ExamIncidentRepository;
import com.chronos.education.scheduling.dao.ExamMaterialHandoverRepository;
import com.chronos.education.scheduling.dao.ExamMaterialLedgerRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamRegistrationRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.model.ExamAccommodation;
import com.chronos.education.scheduling.model.ExamAdmissionTicket;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamIncident;
import com.chronos.education.scheduling.model.ExamIncidentAction;
import com.chronos.education.scheduling.model.ExamMaterialHandover;
import com.chronos.education.scheduling.model.ExamMaterialLedger;
import com.chronos.education.scheduling.model.ExamOperationsCommands;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamRegistration;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.StudentProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Post-scheduling exam operations. No score repository is used here, so this
 * slice cannot mutate an already published score.
 */
@Service
@RequiredArgsConstructor
public class ExamOperationsService {
	private static final List<String> ACTIVE_REGISTRATION_STATUSES = List.of("SUBMITTED", "APPROVED");

	private final ExamRegistrationRepository registrations;
	private final ExamAccommodationRepository accommodations;
	private final ExamAdmissionTicketRepository tickets;
	private final ExamMaterialLedgerRepository ledgers;
	private final ExamMaterialHandoverRepository handovers;
	private final ExamIncidentRepository incidents;
	private final ExamIncidentActionRepository incidentActions;
	private final ExamPlanRepository plans;
	private final ExamSessionRepository sessions;
	private final ExamRoomRepository rooms;
	private final ExamCandidateRepository candidates;
	private final ClassroomRepository classrooms;
	private final StudentProfileRepository students;
	private final ObjectMapper objectMapper;

	/**
	 * The session row lock serializes capacity checks; the database unique key
	 * (session_id, student_id) remains the final idempotency guarantee.
	 */
	@Transactional
	public ExamRegistration register(ExamOperationsCommands.Registration command, String actor) {
		requireText(command.sessionId(), "场次");
		requireText(command.studentId(), "学生");
		ExamSession session = sessions.findLockedById(command.sessionId())
				.orElseThrow(() -> new IllegalArgumentException("考试场次不存在"));
		ExamPlan plan = plan(session.getPlanId());
		if ("CANCELLED".equals(session.getStatus()) || "CANCELLED".equals(plan.getStatus())) {
			throw new IllegalStateException("考试场次已取消");
		}
		ExamRegistration existing = registrations.findBySessionIdAndStudentId(
				session.getId(), command.studentId()).orElse(null);
		if (existing != null) {
			return existing;
		}
		StudentProfile student = students.findById(command.studentId())
				.orElseThrow(() -> new IllegalArgumentException("学生不存在"));
		if (!"ACTIVE".equals(student.getEnrollmentStatus())) {
			throw new IllegalStateException("学生当前不具备考试资格");
		}
		int capacity = sessionCapacity(session.getId());
		if (capacity <= 0) {
			throw new IllegalStateException("考试场次尚未配置有效考场容量");
		}
		long registered = registrations.countBySessionIdAndStatusIn(
				session.getId(), ACTIVE_REGISTRATION_STATUSES);
		if (registered >= capacity) {
			throw new IllegalStateException("考试场次报名人数已达到容量上限");
		}
		ExamRegistration value = new ExamRegistration();
		value.setPlanId(plan.getId());
		value.setSessionId(session.getId());
		value.setStudentId(student.getId());
		value.setSource("STUDENT");
		value.setStatus("SUBMITTED");
		value.setSubmittedAt(LocalDateTime.now());
		value.setCreateBy(actor);
		return registrations.save(value);
	}

	@Transactional(readOnly = true)
	public List<ExamRegistration> registrations(String sessionId) {
		session(sessionId);
		return registrations.findBySessionIdOrderBySubmittedAtAsc(sessionId);
	}

	@Transactional
	public ExamAccommodation requestAccommodation(
			ExamOperationsCommands.Accommodation command,
			String actor) {
		requireText(command.registrationId(), "报名");
		requireText(command.typeCode(), "特殊安排类型");
		if (command.extraMinutes() != null && command.extraMinutes() < 0) {
			throw new IllegalArgumentException("额外考试时长不能为负数");
		}
		ExamRegistration registration = registration(command.registrationId());
		if ("WITHDRAWN".equals(registration.getStatus()) || "REJECTED".equals(registration.getStatus())) {
			throw new IllegalStateException("当前报名状态不允许申请特殊安排");
		}
		ExamAccommodation value = new ExamAccommodation();
		value.setRegistrationId(registration.getId());
		value.setTypeCode(command.typeCode());
		value.setExtraMinutes(command.extraMinutes() == null ? 0 : command.extraMinutes());
		value.setRoomRequirementJson(command.roomRequirementJson());
		value.setFileId(command.fileId());
		value.setStatus("SUBMITTED");
		value.setCreateBy(actor);
		return accommodations.save(value);
	}

	@Transactional
	public ExamAccommodation decideAccommodation(
			String accommodationId,
			ExamOperationsCommands.AccommodationDecision command,
			String actor) {
		ExamAccommodation value = accommodations.findById(accommodationId)
				.orElseThrow(() -> new IllegalArgumentException("特殊安排申请不存在"));
		if (!"SUBMITTED".equals(value.getStatus())) {
			throw new IllegalStateException("特殊安排申请已处理");
		}
		value.setStatus(command.approve() ? "APPROVED" : "REJECTED");
		value.setDecidedBy(actor);
		value.setDecidedAt(LocalDateTime.now());
		return accommodations.save(value);
	}

	@Transactional(readOnly = true)
	public List<ExamAccommodation> accommodations(String registrationId) {
		registration(registrationId);
		return accommodations.findByRegistrationIdOrderByCreateTimeDesc(registrationId);
	}

	/**
	 * Tickets are generated only from the published plan snapshot. Releasing a
	 * later version revokes every ticket issued for an older snapshot.
	 */
	@Transactional
	public List<ExamAdmissionTicket> generateTickets(String planId, String actor) {
		ExamPlan plan = plan(planId);
		if (!"PUBLISHED".equals(plan.getStatus()) || plan.getPublishedVersion() == null
				|| plan.getPublishedVersion() <= 0) {
			throw new IllegalStateException("考试计划尚未通过发布门禁");
		}
		List<ExamAdmissionTicket> result = new ArrayList<>();
		int version = plan.getPublishedVersion();
		for (ExamSession session : sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(planId)) {
			for (ExamRoom room : rooms.findBySessionId(session.getId())) {
				for (ExamCandidate candidate : candidates.findByRoomIdOrderBySeatNoAsc(room.getId())) {
					revokeOlderTickets(candidate.getId(), version);
					if (!"PUBLISHED".equals(session.getStatus())) {
						continue;
					}
					ExamAdmissionTicket ticket = tickets
							.findByCandidateIdAndPublishedVersion(candidate.getId(), version)
							.orElseGet(() -> issueTicket(plan, session, room, candidate, actor));
					result.add(ticket);
				}
			}
		}
		return result;
	}

	@Transactional(readOnly = true)
	public List<ExamAdmissionTicket> tickets(String candidateId) {
		return tickets.findByCandidateIdOrderByPublishedVersionDesc(candidateId);
	}

	@Transactional
	public ExamMaterialLedger createLedger(ExamOperationsCommands.Material command, String actor) {
		requireText(command.sessionId(), "场次");
		requireText(command.materialType(), "物资类型");
		requireText(command.batchNo(), "批次号");
		if (command.plannedQuantity() == null || command.plannedQuantity() <= 0) {
			throw new IllegalArgumentException("计划数量必须大于零");
		}
		session(command.sessionId());
		ExamMaterialLedger value = new ExamMaterialLedger();
		value.setSessionId(command.sessionId());
		value.setMaterialType(command.materialType());
		value.setBatchNo(command.batchNo());
		value.setPlannedQuantity(command.plannedQuantity());
		value.setReceivedQuantity(0);
		value.setSealNo(command.sealNo());
		value.setStatus("OPEN");
		value.setCreateBy(actor);
		return ledgers.save(value);
	}

	@Transactional(readOnly = true)
	public List<ExamMaterialLedger> ledgers(String sessionId) {
		session(sessionId);
		return ledgers.findBySessionIdOrderByCreateTimeDesc(sessionId);
	}

	/**
	 * Every handover records two distinct people. A short handover may be
	 * closed as a discrepancy only with an explicit reason.
	 */
	@Transactional
	public ExamMaterialLedger handover(
			ExamOperationsCommands.Handover command,
			String handoverBy) {
		requireText(command.ledgerId(), "物资台账");
		requireText(command.receivedBy(), "接收人");
		if (handoverBy.equals(command.receivedBy())) {
			throw new IllegalArgumentException("物资交接人和接收人必须是不同人员");
		}
		if (command.quantity() == null || command.quantity() <= 0) {
			throw new IllegalArgumentException("交接数量必须大于零");
		}
		ExamMaterialLedger ledger = ledgers.findById(command.ledgerId())
				.orElseThrow(() -> new IllegalArgumentException("物资台账不存在"));
		if (!"OPEN".equals(ledger.getStatus())) {
			throw new IllegalStateException("物资台账已完成或已登记差异");
		}
		int received = ledger.getReceivedQuantity() + command.quantity();
		if (received > ledger.getPlannedQuantity()) {
			throw new IllegalStateException("交接数量超过计划数量");
		}
		if (received != ledger.getPlannedQuantity()
				&& (command.differenceReason() == null || command.differenceReason().isBlank())) {
			throw new IllegalArgumentException("交接数量与计划不一致时必须填写差异原因");
		}
		ExamMaterialHandover handover = new ExamMaterialHandover();
		handover.setLedgerId(ledger.getId());
		handover.setHandoverBy(handoverBy);
		handover.setReceivedBy(command.receivedBy());
		handover.setHandedAt(LocalDateTime.now());
		handover.setQuantity(command.quantity());
		handover.setDifferenceReason(command.differenceReason());
		handovers.save(handover);
		ledger.setReceivedQuantity(received);
		ledger.setDifferenceReason(command.differenceReason());
		ledger.setStatus(received == ledger.getPlannedQuantity() ? "COMPLETE" : "DISCREPANCY");
		return ledgers.save(ledger);
	}

	@Transactional(readOnly = true)
	public List<ExamMaterialHandover> handovers(String ledgerId) {
		ledger(ledgerId);
		return handovers.findByLedgerIdOrderByHandedAtDesc(ledgerId);
	}

	@Transactional
	public ExamIncident reportIncident(ExamOperationsCommands.Incident command, String actor) {
		requireText(command.sessionId(), "场次");
		requireText(command.incidentType(), "异常类型");
		requireText(command.severity(), "严重程度");
		requireText(command.description(), "异常描述");
		session(command.sessionId());
		if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(command.severity())) {
			throw new IllegalArgumentException("无效的异常严重程度");
		}
		ExamIncident value = new ExamIncident();
		value.setSessionId(command.sessionId());
		value.setRoomId(command.roomId());
		value.setCandidateId(command.candidateId());
		value.setIncidentType(command.incidentType());
		value.setSeverity(command.severity());
		value.setStatus("REPORTED");
		value.setDescription(command.description());
		value.setReportedBy(actor);
		value.setReportedAt(LocalDateTime.now());
		value.setCreateBy(actor);
		return incidents.save(value);
	}

	@Transactional(readOnly = true)
	public List<ExamIncident> incidents(String sessionId) {
		session(sessionId);
		return incidents.findBySessionIdOrderByReportedAtDesc(sessionId);
	}

	@Transactional
	public ExamIncidentAction actOnIncident(
			String incidentId,
			ExamOperationsCommands.IncidentAction command,
			String actor) {
		requireText(command.actionType(), "处置动作");
		requireText(command.conclusion(), "处置结论");
		ExamIncident incident = incident(incidentId);
		String targetStatus = switch (command.actionType()) {
			case "REVIEW" -> "UNDER_REVIEW";
			case "DISPOSE" -> "DISPOSED";
			case "CLOSE" -> "CLOSED";
			default -> throw new IllegalArgumentException("无效的异常处置动作");
		};
		boolean valid = ("REPORTED".equals(incident.getStatus()) && "UNDER_REVIEW".equals(targetStatus))
				|| ("UNDER_REVIEW".equals(incident.getStatus()) && "DISPOSED".equals(targetStatus))
				|| ("DISPOSED".equals(incident.getStatus()) && "CLOSED".equals(targetStatus));
		if (!valid) {
			throw new IllegalStateException("异常状态不允许执行当前处置动作");
		}
		ExamIncidentAction action = new ExamIncidentAction();
		action.setIncidentId(incident.getId());
		action.setActionType(command.actionType());
		action.setActionBy(actor);
		action.setActionAt(LocalDateTime.now());
		action.setConclusion(command.conclusion());
		action.setFileId(command.fileId());
		action.setCreateBy(actor);
		ExamIncidentAction saved = incidentActions.save(action);
		incident.setStatus(targetStatus);
		if ("CLOSED".equals(targetStatus)) {
			incident.setConclusion(command.conclusion());
			incident.setClosedAt(LocalDateTime.now());
		}
		incidents.save(incident);
		return saved;
	}

	@Transactional(readOnly = true)
	public List<ExamIncidentAction> incidentActions(String incidentId) {
		incident(incidentId);
		return incidentActions.findByIncidentIdOrderByActionAtAsc(incidentId);
	}

	private ExamAdmissionTicket issueTicket(
			ExamPlan plan,
			ExamSession session,
			ExamRoom room,
			ExamCandidate candidate,
			String actor) {
		ExamAdmissionTicket ticket = new ExamAdmissionTicket();
		ticket.setCandidateId(candidate.getId());
		ticket.setPublishedVersion(plan.getPublishedVersion());
		ticket.setTicketNo("EXAM-" + plan.getId() + "-" + plan.getPublishedVersion()
				+ "-" + candidate.getId());
		ticket.setSeatSnapshotJson(snapshot(plan, session, room, candidate));
		ticket.setIssuedAt(LocalDateTime.now());
		ticket.setStatus("ISSUED");
		ticket.setCreateBy(actor);
		return tickets.save(ticket);
	}

	private void revokeOlderTickets(String candidateId, int version) {
		for (ExamAdmissionTicket old : tickets.findByCandidateIdOrderByPublishedVersionDesc(candidateId)) {
			if (old.getPublishedVersion() < version && "ISSUED".equals(old.getStatus())) {
				old.setStatus("REVOKED");
				old.setRevokedAt(LocalDateTime.now());
				tickets.save(old);
			}
		}
	}

	private String snapshot(
			ExamPlan plan,
			ExamSession session,
			ExamRoom room,
			ExamCandidate candidate) {
		Map<String, Object> snapshot = new LinkedHashMap<>();
		snapshot.put("planId", plan.getId());
		snapshot.put("sessionId", session.getId());
		snapshot.put("examDate", session.getExamDate());
		snapshot.put("startTime", session.getStartTime());
		snapshot.put("endTime", session.getEndTime());
		snapshot.put("roomId", room.getId());
		snapshot.put("classroomId", room.getClassroomId());
		snapshot.put("candidateId", candidate.getId());
		snapshot.put("studentId", candidate.getStudentId());
		snapshot.put("seatNo", candidate.getSeatNo());
		try {
			return objectMapper.writeValueAsString(snapshot);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("准考证座位快照生成失败", ex);
		}
	}

	private int sessionCapacity(String sessionId) {
		return rooms.findBySessionId(sessionId).stream()
				.map(ExamRoom::getClassroomId)
				.map(classrooms::findById)
				.flatMap(java.util.Optional::stream)
				.filter(room -> Boolean.TRUE.equals(room.getEnabled()))
				.mapToInt(room -> room.getCapacity() == null ? 0 : room.getCapacity())
				.sum();
	}

	private ExamPlan plan(String id) {
		return plans.findById(id).orElseThrow(() -> new IllegalArgumentException("考试计划不存在"));
	}

	private ExamSession session(String id) {
		return sessions.findById(id).orElseThrow(() -> new IllegalArgumentException("考试场次不存在"));
	}

	private ExamRegistration registration(String id) {
		return registrations.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("考试报名不存在"));
	}

	private ExamMaterialLedger ledger(String id) {
		return ledgers.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("物资台账不存在"));
	}

	private ExamIncident incident(String id) {
		return incidents.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("考试异常不存在"));
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + "不能为空");
		}
	}
}
