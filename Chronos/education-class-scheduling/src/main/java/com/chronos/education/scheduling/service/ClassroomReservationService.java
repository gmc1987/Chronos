package com.chronos.education.scheduling.service;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomReservationRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
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
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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
	private final IOrganizationRepository organizations;
	private final AcademicDataService academic;
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
			IOrganizationRepository organizations,
			AcademicDataService academic,
			ClassroomReservationConflictService conflicts,
			EducationResourceTransactionLock transactionLock,
			EducationDataScopeService dataScopes,
			EducationApplicantResolver applicants,
			WorkflowNotificationService notifications,
			IAuditLogService audit) {
		this.reservations = reservations;
		this.classrooms = classrooms;
		this.terms = terms;
		this.organizations = organizations;
		this.academic = academic;
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
		if (existing != null && Set.of("ACTIVE", "CANCELLED").contains(existing.getStatus())) {
			return existing;
		}

		ClassroomReservation reservation = existing == null
				? fromEvent(event)
				: existing;
		reserve(reservation);
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
		if (Set.of("ACTIVE", "CANCELLED").contains(reservation.getStatus())) {
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
		return cancel(username, id, reason, administrator, false);
	}

	@Transactional
	public ClassroomReservation cancelWithinScope(String username, String id, String reason) {
		return cancel(username, id, reason, true, true);
	}

	private ClassroomReservation cancel(String username, String id, String reason, boolean administrator,
			boolean scopedAdministrator) {
		ClassroomReservation reservation = reservations.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("教室申请记录不存在"));
		if (scopedAdministrator) {
			dataScopes.assertClassroomAccess(dataScopes.resolve(username), reservation.getClassroomId());
		}
		if (!administrator && !username.equals(reservation.getApplicantUsername())) {
			throw new AccessDeniedException("只能撤销本人提交的教室申请");
		}
		if (!Set.of("ACTIVE", "PENDING").contains(reservation.getStatus())) {
			throw new IllegalStateException("当前教室申请不能撤销");
		}
		if (!administrator && !reservation.getUsageDate().isAfter(LocalDate.now())) {
			throw new IllegalStateException("当天及历史教室申请不能由申请人自行撤销");
		}
		reservation.setStatus("CANCELLED");
		reservation.setCancellationReason(requiredNote(reason, "撤销原因"));
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
		reserve(reservation);
		reservation.setStatus("ACTIVE");
		reservation.setFailureMessage(null);
		audit.log(username, "EDUCATION_CLASSROOM_RESERVATION_RETRIED", "reservationId=" + id);
		return reservations.save(reservation);
	}


	public record TermSetup(String academicYear, Integer termNo, String name,
			LocalDate startDate, LocalDate endDate, Integer weeks) {}

	public record Application(String semesterCode, String classroomId, LocalDate usageDate,
			int startPeriod, int durationPeriods, int attendeeCount, String purpose) {}

	@Transactional(readOnly = true)
	public List<AcademicTerm> reservationTerms(String username, String classroomId) {
		dataScopes.assertReservableClassroom(username, classroomId);
		return terms.findByOrganizationIdOrderByStartDateDesc(classroomSchool(classroomId)).stream()
				.filter(term -> "ACTIVE".equals(term.getStatus()))
				.toList();
	}

	@Transactional
	public AcademicTerm createTerm(String username, String schoolId, TermSetup command) {
		var scope = dataScopes.resolve(username);
		if (!scope.fullAccess() && !scope.campusIds().contains(schoolId)) {
			throw new AccessDeniedException("无权维护该学校的预约学期");
		}
		if (organizations.findById(schoolId)
				.filter(org -> "SCHOOL".equals(org.getOrganizationType()) && Integer.valueOf(1).equals(org.getStatus()))
				.isEmpty()) {
			throw new IllegalArgumentException("请选择有权管理的学校");
		}
		var term = new AcademicTerm();
		term.setOrganizationId(schoolId);
		term.setAcademicYear(command.academicYear());
		term.setTermNo(command.termNo());
		term.setTermName(command.name());
		term.setStartDate(command.startDate());
		term.setEndDate(command.endDate());
		term.setWeekCount(command.weeks());
		if (term.getAcademicYear() == null || !term.getAcademicYear().matches("[0-9]{4}-[0-9]{4}")
				|| Integer.parseInt(term.getAcademicYear().substring(0, 4)) + 1
						!= Integer.parseInt(term.getAcademicYear().substring(5))
				|| term.getTermName() == null || term.getTermName().isBlank() || term.getTermName().length() > 128
				|| term.getTermNo() == null || term.getTermNo() < 1 || term.getTermNo() > 3
				|| term.getWeekCount() == null || term.getWeekCount() < 1 || term.getWeekCount() > 53) {
			throw new IllegalArgumentException("请填写学年、学期名称、学期序号和教学周数");
		}
		term.setLocalTermCode(term.getAcademicYear().trim() + "-" + term.getTermNo());
		term = academic.saveTerm(null, term);
		audit.log(username, "EDUCATION_VENUE_TERM_CREATED", "termId=" + term.getId());
		return term;
	}

	@Transactional(readOnly = true)
	public void check(String username, Application command) {
		dataScopes.assertReservableClassroom(username, command.classroomId());
		assertActiveTerm(command.semesterCode(), command.classroomId());
		if (command.usageDate() == null || !command.usageDate().isAfter(LocalDate.now())) {
			throw new IllegalArgumentException("请选择今天之后的使用日期");
		}
		if (command.startPeriod() < 1 || command.durationPeriods() < 1
				|| (long) command.startPeriod() + command.durationPeriods() - 1 > 30) {
			throw new IllegalArgumentException("使用节次必须在第 1 至 30 节内");
		}
		conflicts.assertAvailable(command.semesterCode(), command.classroomId(), command.usageDate(),
				command.startPeriod(), command.durationPeriods(), command.attendeeCount(), null);
	}

	@Transactional
	public ClassroomReservationView submit(String username, Application command) {
		check(username, command);
		var reservation = new ClassroomReservation();
		reservation.setBusinessKey("VENUE-" + UUID.randomUUID());
		reservation.setApplicantUsername(username);
		reservation.setSemesterCode(command.semesterCode());
		reservation.setClassroomId(command.classroomId());
		reservation.setUsageDate(command.usageDate());
		reservation.setStartPeriod(command.startPeriod());
		reservation.setDurationPeriods(command.durationPeriods());
		reservation.setAttendeeCount(command.attendeeCount());
		reservation.setPurpose(requiredNote(command.purpose(), "用途"));
		reservation.setStatus("PENDING");
		reservation = reservations.save(reservation);
		audit.log(username, "EDUCATION_VENUE_REQUESTED", "reservationId=" + reservation.getId());
		return view(reservation);
	}

	public record DeskPage(List<ClassroomReservationView> content, long totalElements) {}

	@Transactional(readOnly = true)
	public DeskPage scopedCalendar(String username, String organizationId,
			LocalDate startDate, LocalDate endDate, String status, int page, int size) {
		var campuses = dataScopes.scheduleOrganizationCampuses(dataScopes.resolve(username), organizationId);
		if (campuses == null || campuses.isEmpty()) {
			throw new AccessDeniedException("请选择有权查看的学校或校区");
		}
		if (endDate.isBefore(startDate) || endDate.isAfter(startDate.plusDays(92))) {
			throw new IllegalArgumentException("查询日期范围不能超过 93 天");
		}
		var result = reservations.findForCampuses(campuses, startDate, endDate, status,
				PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100)));
		return new DeskPage(result.map(this::view).getContent(), result.getTotalElements());
	}

	@Transactional
	public ClassroomReservationView review(String username, String id, boolean approve, String note) {
		var reservation = reservations.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("教室申请记录不存在"));
		dataScopes.assertClassroomAccess(dataScopes.resolve(username), reservation.getClassroomId());
		if (!"PENDING".equals(reservation.getStatus()) || reservation.getWorkflowInstanceId() != null) {
			throw new IllegalStateException("此教室申请已处理或需通过原审批流程处理");
		}
		if (username.equals(reservation.getApplicantUsername())) {
			throw new AccessDeniedException("申请人不能审核自己的教室申请");
		}
		if (approve) {
			if (!reservation.getUsageDate().isAfter(LocalDate.now())) {
				throw new IllegalStateException("使用日期已到，无法批准申请");
			}
			assertActiveTerm(reservation.getSemesterCode(), reservation.getClassroomId());
			reserve(reservation);
		}
		reservation.setReviewNote(approve && (note == null || note.isBlank())
				? null : requiredNote(note, "审核意见"));
		reservation.setApprovedBy(username);
		reservation.setReviewedAt(LocalDateTime.now());
		reservation.setStatus(approve ? "ACTIVE" : "REJECTED");
		audit.log(username, approve ? "EDUCATION_VENUE_APPROVED" : "EDUCATION_VENUE_REJECTED",
				"reservationId=" + id);
		return view(reservations.save(reservation));
	}

	private void assertActiveTerm(String semesterCode, String classroomId) {
		var term = terms.findByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("请选择教室所属学校的学期"));
		if (!"ACTIVE".equals(term.getStatus()) || !classroomSchool(classroomId).equals(term.getOrganizationId())) {
			throw new IllegalArgumentException("请选择教室所属学校的启用学期");
		}
	}

	private String classroomSchool(String classroomId) {
		return classrooms.findById(classroomId)
				.map(Classroom::getCampusId)
				.flatMap(organizations::findById)
				.map(org -> "SCHOOL".equals(org.getOrganizationType()) ? org : org.getParentOrgId())
				.filter(org -> "SCHOOL".equals(org.getOrganizationType()))
				.orElseThrow(() -> new IllegalArgumentException("教室未关联学校"))
				.getId();
	}

	private void reserve(ClassroomReservation reservation) {
		// 保持与考试发布、调课使用相同的学期锁，再校验跨学期的场地占用。
		transactionLock.lockSemester(reservation.getSemesterCode());
		transactionLock.lockClassroomDate(reservation.getClassroomId(), reservation.getUsageDate());
		conflicts.assertAvailable(reservation.getSemesterCode(), reservation.getClassroomId(),
				reservation.getUsageDate(), reservation.getStartPeriod(), reservation.getDurationPeriods(),
				reservation.getAttendeeCount(), reservation.getId());
	}

	private String requiredNote(String value, String label) {
		String result = required(value, label + "不能为空");
		if (result.length() > 1000) {
			throw new IllegalArgumentException(label + "不能超过 1000 字");
		}
		return result;
	}

	@Transactional(readOnly = true)
	public List<Map<String, Object>> classroomOptions(String username) {
		// 选项接口仍按账号数据范围过滤，避免通过流程表单枚举无权访问的校区资源。
		var scope = dataScopes.resolve(username);
		var reservableCampusIds = dataScopes.reservableCampusIds(username);
		return classrooms.findByEnabledTrueOrderByRoomCode().stream()
				.filter(classroom -> scope.fullAccess() || classroom.getCampusId() != null
						&& reservableCampusIds.contains(classroom.getCampusId()))
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
				reservation.getCancellationReason(),
				reservation.getApprovedBy(),
				reservation.getReviewedAt(),
				reservation.getReviewNote());
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
