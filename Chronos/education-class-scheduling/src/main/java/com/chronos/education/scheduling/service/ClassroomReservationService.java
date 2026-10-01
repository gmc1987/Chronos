package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomReservationRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.ClassroomReservation;
import com.chronos.education.scheduling.model.ClassroomReservationView;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowNotificationService;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 教室申请审批回写、查询、撤销和失败重放。 */
@Service
public class ClassroomReservationService {
	private final ClassroomReservationRepository reservations;
	private final ClassroomRepository classrooms;
	private final AcademicTermRepository terms;
	private final ClassroomReservationConflictService conflicts;
	private final EducationResourceTransactionLock transactionLock;
	private final EducationDataScopeService dataScopes;
	private final EducationApplicantResolver applicants;
	private final WorkflowNotificationService notifications;
	private final IAuditLogService audit;

	public ClassroomReservationService(
			ClassroomReservationRepository reservations,
			ClassroomRepository classrooms,
			AcademicTermRepository terms,
			ClassroomReservationConflictService conflicts,
			EducationResourceTransactionLock transactionLock,
			EducationDataScopeService dataScopes,
			EducationApplicantResolver applicants,
			WorkflowNotificationService notifications,
			IAuditLogService audit) {
		this.reservations = reservations;
		this.classrooms = classrooms;
		this.terms = terms;
		this.conflicts = conflicts;
		this.transactionLock = transactionLock;
		this.dataScopes = dataScopes;
		this.applicants = applicants;
		this.notifications = notifications;
		this.audit = audit;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ClassroomReservation apply(WorkflowCompletedEvent event) {
		ClassroomReservation existing = reservations
				.findByWorkflowInstanceId(event.instanceId())
				.orElse(null);
		if (existing != null && "ACTIVE".equals(existing.getStatus())) {
			return existing;
		}

		ClassroomReservation reservation = existing == null
				? fromEvent(event)
				: existing;
		// 学期级数据库事务锁让考试发布、调课和教室申请的最终检查串行执行。
		transactionLock.lockSemester(reservation.getSemesterCode());
		conflicts.assertAvailable(
				reservation.getSemesterCode(),
				reservation.getClassroomId(),
				reservation.getUsageDate(),
				reservation.getStartPeriod(),
				reservation.getDurationPeriods(),
				reservation.getAttendeeCount(),
				reservation.getId());
		reservation.setStatus("ACTIVE");
		reservation.setFailureMessage(null);
		reservation.setApprovedBy(event.completedBy());
		reservation = reservations.save(reservation);

		notifications.enqueueWorkflowMessage(
				event.instanceId(),
				"_CLASSROOM_RESERVED",
				event.initiatedBy(),
				"教室申请已生效",
				"申请单 " + event.businessKey() + " 已完成审批并占用教室。",
				"CLASSROOM_RESERVED");
		audit.log(
				event.completedBy(),
				"EDUCATION_CLASSROOM_RESERVATION_APPLIED",
				"reservationId=" + reservation.getId() + ",workflowInstanceId=" + event.instanceId());
		return reservation;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordFailure(WorkflowCompletedEvent event, RuntimeException exception) {
		ClassroomReservation reservation = reservations
				.findByWorkflowInstanceId(event.instanceId())
				.orElseGet(() -> fromEvent(event));
		if ("ACTIVE".equals(reservation.getStatus())) {
			return;
		}
		reservation.setStatus("APPLY_FAILED");
		reservation.setFailureMessage(limit(exception.getMessage()));
		reservations.save(reservation);
		audit.log(
				event.completedBy(),
				"EDUCATION_CLASSROOM_RESERVATION_FAILED",
				"workflowInstanceId=" + event.instanceId() + ",error=" + limit(exception.getMessage()));
	}

	@Transactional(readOnly = true)
	public List<ClassroomReservationView> mine(String username) {
		return reservations.findByApplicantUsernameOrderByUsageDateDesc(username)
				.stream()
				.map(this::view)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ClassroomReservationView> calendar(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
			throw new IllegalArgumentException("查询日期范围不正确");
		}
		return reservations.findByUsageDateBetweenOrderByUsageDateAscStartPeriodAsc(
				startDate,
				endDate)
				.stream()
				.map(this::view)
				.toList();
	}

	@Transactional
	public ClassroomReservation cancel(String username, String id, String reason, boolean administrator) {
		ClassroomReservation reservation = reservations.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("教室申请记录不存在"));
		if (!administrator && !username.equals(reservation.getApplicantUsername())) {
			throw new AccessDeniedException("只能撤销本人提交的教室申请");
		}
		if (!"ACTIVE".equals(reservation.getStatus())) {
			throw new IllegalStateException("当前教室申请不能撤销");
		}
		if (!administrator && !reservation.getUsageDate().isAfter(LocalDate.now())) {
			throw new IllegalStateException("当天及历史教室申请不能由申请人自行撤销");
		}
		reservation.setStatus("CANCELLED");
		reservation.setCancellationReason(required(reason, "撤销原因不能为空"));
		reservation.setCancelledBy(username);
		reservation.setCancelledAt(LocalDateTime.now());
		audit.log(username, "EDUCATION_CLASSROOM_RESERVATION_CANCELLED", "reservationId=" + id);
		return reservations.save(reservation);
	}

	@Transactional
	public ClassroomReservation retry(String username, String id) {
		ClassroomReservation reservation = reservations.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("教室申请记录不存在"));
		if (!"APPLY_FAILED".equals(reservation.getStatus())) {
			throw new IllegalStateException("只有回写失败的教室申请可以重试");
		}
		transactionLock.lockSemester(reservation.getSemesterCode());
		conflicts.assertAvailable(
				reservation.getSemesterCode(),
				reservation.getClassroomId(),
				reservation.getUsageDate(),
				reservation.getStartPeriod(),
				reservation.getDurationPeriods(),
				reservation.getAttendeeCount(),
				reservation.getId());
		reservation.setStatus("ACTIVE");
		reservation.setFailureMessage(null);
		audit.log(username, "EDUCATION_CLASSROOM_RESERVATION_RETRIED", "reservationId=" + id);
		return reservations.save(reservation);
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> classroomOptions(String username) {
		// 选项接口仍按账号数据范围过滤，避免通过流程表单枚举无权访问的校区资源。
		var scope = dataScopes.resolve(username);
		return classrooms.findByEnabledTrueOrderByRoomCode().stream()
				.filter(classroom -> scope.fullAccess()
						|| classroom.getCampusId() != null
						&& scope.campusIds().contains(classroom.getCampusId()))
				.map(this::classroomOption)
				.toList();
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> termOptions() {
		return terms.findAllByOrderByStartDateDesc().stream()
				.filter(term -> "ACTIVE".equals(term.getStatus()))
				.map(term -> {
					Map<String, Object> option = new LinkedHashMap<>();
					option.put("value", term.getTermCode());
					option.put("label", term.getTermName());
					return option;
				})
				.toList();
	}

	private Map<String, Object> classroomOption(Classroom classroom) {
		Map<String, Object> option = new LinkedHashMap<>();
		option.put("value", classroom.getId());
		option.put("label", classroom.getRoomCode() + " · " + classroom.getRoomName()
				+ "（" + classroom.getCapacity() + "人）");
		return option;
	}

	private ClassroomReservationView view(ClassroomReservation reservation) {
		String classroomName = classrooms.findById(reservation.getClassroomId())
				.map(classroom -> classroom.getRoomCode() + " · " + classroom.getRoomName())
				.orElse("教室已删除");
		return new ClassroomReservationView(
				reservation.getId(),
				reservation.getBusinessKey(),
				reservation.getApplicantUsername(),
				reservation.getSemesterCode(),
				reservation.getClassroomId(),
				classroomName,
				reservation.getUsageDate(),
				reservation.getStartPeriod(),
				reservation.getDurationPeriods(),
				reservation.getAttendeeCount(),
				reservation.getPurpose(),
				reservation.getStatus(),
				reservation.getFailureMessage(),
				reservation.getCancellationReason());
	}

	private ClassroomReservation fromEvent(WorkflowCompletedEvent event) {
		Map<String, Object> form = event.mainFormData();
		ClassroomReservation reservation = new ClassroomReservation();
		reservation.setWorkflowInstanceId(event.instanceId());
		reservation.setBusinessKey(event.businessKey());
		reservation.setApplicantUsername(event.initiatedBy());
		reservation.setApplicantId(applicants.resolve(event.initiatedBy(), "TEACHER"));
		reservation.setSemesterCode(required(form.get("semesterCode"), "学期不能为空"));
		reservation.setClassroomId(required(form.get("classroomId"), "教室不能为空"));
		reservation.setUsageDate(LocalDate.parse(required(form.get("usageDate"), "使用日期不能为空")));
		reservation.setStartPeriod(integer(form.get("startPeriod"), "开始节次不正确"));
		reservation.setDurationPeriods(integer(form.get("durationPeriods"), "持续节数不正确"));
		reservation.setAttendeeCount(integer(form.get("attendeeCount"), "使用人数不正确"));
		reservation.setPurpose(required(form.get("purpose"), "用途不能为空"));
		reservation.setStatus("APPLYING");
		return reservation;
	}

	private int integer(Object value, String message) {
		try {
			return Integer.parseInt(required(value, message));
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(message);
		}
	}

	private String required(Object value, String message) {
		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return String.valueOf(value).trim();
	}

	private String limit(String message) {
		String value = message == null ? "未知错误" : message;
		return value.length() <= 1000 ? value : value.substring(0, 1000);
	}
}
