package com.chronos.education.scheduling.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.service.iService.IAuditLogService;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.AdministrativeClassRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamCourseSuspensionItemRepository;
import com.chronos.education.scheduling.dao.ExamCourseSuspensionRequestRepository;
import com.chronos.education.scheduling.dao.ExamInvigilationChangeRepository;
import com.chronos.education.scheduling.dao.ExamInvigilationRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamPublishedChangeRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.ExamTeacherQualificationRepository;
import com.chronos.education.scheduling.dao.LeaveRequestRecordRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.SubjectRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamCourseSuspensionItem;
import com.chronos.education.scheduling.model.ExamCourseSuspensionRequest;
import com.chronos.education.scheduling.model.ExamCommands;
import com.chronos.education.scheduling.model.ExamDutyView;
import com.chronos.education.scheduling.model.ExamInvigilation;
import com.chronos.education.scheduling.model.ExamInvigilationChange;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamPublishedChange;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.ExamStudentView;
import com.chronos.education.scheduling.model.ExamTeacherSuggestion;
import com.chronos.education.scheduling.model.ExamTeacherQualification;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
import com.chronos.education.scheduling.model.ScheduleDateException;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;

import lombok.RequiredArgsConstructor;

/** 考试业务入口。计划发布前统一检查具体日期的课表和考试资源占用。 */
@Service
@RequiredArgsConstructor
public class ExamCenterService {
	private final ExamPlanRepository plans;
	private final ExamSessionRepository sessions;
	private final ExamRoomRepository rooms;
	private final ExamCandidateRepository candidates;
	private final ExamInvigilationRepository assignments;
	private final ExamInvigilationChangeRepository changes;
	private final AcademicTermRepository terms;
	private final SubjectRepository subjects;
	private final ClassroomRepository classrooms;
	private final StudentProfileRepository students;
	private final AdministrativeClassRepository administrativeClasses;
	private final TeacherAcademicProfileRepository teachers;
	private final TeachingClassMemberRepository teachingClassMembers;
	private final TeacherTimeConstraintRepository teacherConstraints;
	private final LeaveRequestRecordRepository leaveRecords;
	private final ExamTeacherQualificationRepository qualifications;
	private final ExamCourseSuspensionRequestRepository suspensionRequests;
	private final ExamCourseSuspensionItemRepository suspensionItems;
	private final ExamPublishedChangeRepository publishedChanges;
	private final ScheduleEntryRepository scheduleEntries;
	private final ExamResourceReservationService examReservations;
	private final CourseOfferingRepository offerings;
	private final BellScheduleRepository bellSchedules;
	private final BellPeriodRepository bellPeriods;
	private final ScheduleOccurrenceService occurrences;
	private final EducationDataScopeService dataScopes;
	private final ExamNotificationService examNotifications;
	private final ExamPaperAnalysisService paperAnalysis;
	private final EducationResourceTransactionLock resourceLock;
	private final IAuditLogService audit;

	@Transactional(readOnly = true)
	public List<ExamPlan> plans(String semesterCode) {
		return plans.findBySemesterCodeOrderByStartDateAsc(semesterCode);
	}

	@Transactional(readOnly = true)
	public List<ExamSession> sessions(String planId) {
		plan(planId);
		return sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(planId);
	}

	@Transactional(readOnly = true)
	public List<ExamRoom> rooms(String sessionId) {
		session(sessionId);
		return rooms.findBySessionId(sessionId);
	}

	@Transactional(readOnly = true)
	public List<ExamCandidate> candidates(String roomId) {
		room(roomId);
		return candidates.findByRoomIdOrderBySeatNoAsc(roomId);
	}

	@Transactional(readOnly = true)
	public List<ExamInvigilation> assignments(String roomId) {
		room(roomId);
		return assignments.findByRoomIdAndStatus(roomId, "ASSIGNED");
	}

	@Transactional(readOnly = true)
	public List<ExamInvigilation> standbys(String sessionId) {
		session(sessionId);
		return rooms.findBySessionId(sessionId).stream()
				.flatMap(room -> assignments
						.findByRoomIdAndStatus(room.getId(), "STANDBY").stream())
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ExamInvigilationChange> pendingChanges() {
		return changes.findByStatusOrderByCreateTimeDesc("PENDING");
	}

	@Transactional(readOnly = true)
	public List<ExamDutyView> myDuties(String username) {
		return dataScopes.resolve(username).teacherIds().stream()
				.flatMap(teacherId -> assignments
						.findByTeacherIdAndStatusIn(
								teacherId, List.of("ASSIGNED", "STANDBY"))
						.stream())
				.map(assignment -> {
					ExamRoom room = room(assignment.getRoomId());
					ExamSession session = session(room.getSessionId());
					ExamPlan plan = plan(session.getPlanId());
					Classroom classroom = classrooms.findById(room.getClassroomId())
							.orElseThrow();
					return new ExamDutyView(
							assignment.getId(), plan.getPlanName(),
							session.getSubjectId(),
							subjects.findById(session.getSubjectId())
									.orElseThrow().getSubjectName(),
							session.getExamDate(),
							session.getStartTime(), session.getEndTime(),
							classroom.getRoomName(), assignment.getDutyRole(),
							session.getStatus(), assignment.getAcknowledgedAt(),
							assignment.getStatus(), assignment.getCheckedInAt());
				})
				.filter(item -> "PUBLISHED".equals(item.status()))
				.sorted(Comparator.comparing(ExamDutyView::examDate)
						.thenComparing(ExamDutyView::startTime))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ExamStudentView> myExams(String username) {
		return dataScopes.resolve(username).studentIds().stream()
				.flatMap(studentId -> candidates.findByStudentId(studentId).stream())
				.map(candidate -> {
					ExamRoom room = room(candidate.getRoomId());
					ExamSession session = session(room.getSessionId());
					if (!"PUBLISHED".equals(session.getStatus())) {
						return null;
					}
					ExamPlan plan = plan(session.getPlanId());
					Classroom classroom = classrooms.findById(room.getClassroomId())
							.orElseThrow();
					return new ExamStudentView(
							candidate.getId(), plan.getPlanName(),
							session.getSubjectId(),
							subjects.findById(session.getSubjectId())
									.orElseThrow().getSubjectName(),
							session.getExamDate(),
							session.getStartTime(), session.getEndTime(),
							classroom.getRoomName(), candidate.getSeatNo());
				})
				.filter(Objects::nonNull)
				.sorted(Comparator.comparing(ExamStudentView::examDate)
						.thenComparing(ExamStudentView::startTime))
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ExamInvigilationChange> myChanges(String username) {
		return changes.findByRequestedByOrderByCreateTimeDesc(username);
	}

	@Transactional
	public ExamInvigilation acknowledgeDuty(String assignmentId, String username) {
		ExamInvigilation assignment = assignment(assignmentId);
		if (!dataScopes.resolve(username).teacherIds()
				.contains(assignment.getTeacherId())) {
			throw new AccessDeniedException("只能确认本人监考任务");
		}
		ExamSession session = session(room(assignment.getRoomId()).getSessionId());
		if (!"PUBLISHED".equals(session.getStatus())) {
			throw new IllegalStateException("考试尚未发布");
		}
		if (assignment.getAcknowledgedAt() == null) {
			assignment.setAcknowledgedAt(LocalDateTime.now());
		}
		return assignments.save(assignment);
	}

	@Transactional
	public ExamInvigilation checkInDuty(String assignmentId, String username) {
		ExamInvigilation assignment = assignment(assignmentId);
		if (!dataScopes.resolve(username).teacherIds()
				.contains(assignment.getTeacherId())) {
			throw new AccessDeniedException("只能为本人监考任务报到");
		}
		if (!Set.of("ASSIGNED", "STANDBY").contains(assignment.getStatus())) {
			throw new IllegalStateException("该监考任务已失效");
		}
		ExamSession session = session(room(assignment.getRoomId()).getSessionId());
		if (!"PUBLISHED".equals(session.getStatus())) {
			throw new IllegalStateException("考试尚未发布");
		}
		LocalDateTime startsAt = LocalDateTime.of(
				session.getExamDate(), session.getStartTime());
		LocalDateTime now = LocalDateTime.now();
		if (now.isBefore(startsAt.minusMinutes(90))
				|| now.isAfter(startsAt.plusMinutes(15))) {
			throw new IllegalStateException("仅能在考前 90 分钟至开考后 15 分钟内报到");
		}
		if (assignment.getCheckedInAt() == null) {
			assignment.setCheckedInAt(now);
		}
		return assignments.save(assignment);
	}

	@Transactional
	public ExamInvigilation addStandby(String roomId, String teacherId) {
		ExamRoom room = room(roomId);
		ExamSession session = session(room.getSessionId());
		resourceLock.lockSemester(draft(session.getPlanId()).getSemesterCode());
		assertTeacherAvailable(teacherId, session, null, roomId);
		ExamInvigilation value = new ExamInvigilation();
		value.setRoomId(roomId);
		value.setTeacherId(teacherId);
		value.setDutyRole("STANDBY");
		value.setStatus("STANDBY");
		return assignments.save(value);
	}

	/** 缺勤记录与机动教师接替在同一事务内；没有机动教师时保留缺勤记录供考务员手工处置。 */
	@Transactional
	public ExamInvigilation markAbsent(String assignmentId, String actor) {
		ExamInvigilation absent = assignment(assignmentId);
		ExamRoom targetRoom = room(absent.getRoomId());
		ExamSession session = session(targetRoom.getSessionId());
		resourceLock.lockSemester(plan(session.getPlanId()).getSemesterCode());
		if (!"PUBLISHED".equals(session.getStatus())
				|| !"ASSIGNED".equals(absent.getStatus())
				|| absent.getCheckedInAt() != null) {
			throw new IllegalStateException("只能登记未报到的已发布监考任务为缺勤");
		}
		absent.setStatus("ABSENT");
		absent.setAbsentAt(LocalDateTime.now());
		List<ExamInvigilation> pool = standbys(session.getId());
		for (ExamInvigilation standby : pool) {
			if (!teacherConflicts(standby.getTeacherId(), session,
					standby.getId(), targetRoom.getId()).isEmpty()) {
				continue;
			}
			standby.setRoomId(targetRoom.getId());
			standby.setDutyRole(absent.getDutyRole());
			standby.setStatus("ASSIGNED");
			absent.setReplacedById(standby.getId());
			assignments.save(standby);
			examNotifications.standbyActivated(standby.getTeacherId(), standby.getId());
			break;
		}
		ExamInvigilation saved = assignments.save(absent);
		audit.log(actor, "EDUCATION_INVIGILATION_ABSENT",
				"assignmentId=" + assignmentId + ", replacementId=" + saved.getReplacedById());
		return saved;
	}

	@Transactional
	public int escalateMissingCheckIns() {
		LocalDateTime now = LocalDateTime.now();
		int count = 0;
		for (ExamInvigilation duty : assignments
				.findByStatusAndCheckedInAtIsNullAndAbsenceEscalatedAtIsNull("ASSIGNED")) {
			ExamSession session = session(room(duty.getRoomId()).getSessionId());
			LocalDateTime startsAt = LocalDateTime.of(
					session.getExamDate(), session.getStartTime());
			if (!"PUBLISHED".equals(session.getStatus())
					|| now.isBefore(startsAt.minusMinutes(15))
					|| now.isAfter(startsAt.plusHours(2))) {
				continue;
			}
			duty.setAbsenceEscalatedAt(now);
			assignments.save(duty);
			examNotifications.missingCheckIn(duty.getId(), duty.getTeacherId());
			count++;
		}
		return count;
	}

	@Transactional
	public ExamPlan savePlan(String id, ExamCommands.Plan command) {
		required(command.semesterCode(), "学期");
		resourceLock.lockSemester(command.semesterCode());
		required(command.planName(), "考试计划名称");
		required(command.examType(), "考试类型");
		AcademicTerm term = terms.findByTermCode(command.semesterCode())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (command.startDate() == null || command.endDate() == null
				|| command.endDate().isBefore(command.startDate())
				|| command.startDate().isBefore(term.getStartDate())
				|| command.endDate().isAfter(term.getEndDate())) {
			throw new IllegalArgumentException("考试日期必须位于学期内且起止有效");
		}
		ExamPlan value = id == null ? new ExamPlan() : plan(id);
		if (!"DRAFT".equals(value.getStatus())) {
			throw new IllegalStateException("已发布考试计划须走变更流程");
		}
		if (id != null && !value.getSemesterCode().equals(command.semesterCode())
				&& !sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(id).isEmpty()) {
			throw new IllegalStateException("已有考试场次时不能修改学期");
		}
		if (id != null && sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(id)
				.stream().anyMatch(item -> item.getExamDate().isBefore(command.startDate())
						|| item.getExamDate().isAfter(command.endDate()))) {
			throw new IllegalStateException("计划日期不能排除已有场次");
		}
		int baseStaff = command.baseInvigilators() == null ? 2 : command.baseInvigilators();
		int threshold = command.extraStaffThreshold() == null
				? 60 : command.extraStaffThreshold();
		if (baseStaff < 1 || threshold < 1) {
			throw new IllegalArgumentException("监考人数规则必须为正整数");
		}
		int maxConsecutive = command.maxConsecutiveDuties() == null
				? 2 : command.maxConsecutiveDuties();
		int travelMinutes = command.campusTravelMinutes() == null
				? 60 : command.campusTravelMinutes();
		if (maxConsecutive < 1 || travelMinutes < 0 || travelMinutes > 480) {
			throw new IllegalArgumentException("连续监考上限或跨校区通勤时间无效");
		}
		value.setSemesterCode(command.semesterCode());
		value.setPlanName(command.planName());
		value.setExamType(command.examType());
		value.setStartDate(command.startDate());
		value.setEndDate(command.endDate());
		value.setBaseInvigilators(baseStaff);
		value.setExtraStaffThreshold(threshold);
		value.setAllowOwnClassInvigilation(Boolean.TRUE.equals(
				command.allowOwnClassInvigilation()));
		value.setRuleJson(command.ruleJson());
		value.setMaxConsecutiveDuties(maxConsecutive);
		value.setCampusTravelMinutes(travelMinutes);
		value.setRequireSubjectQualification(Boolean.TRUE.equals(
				command.requireSubjectQualification()));
		return plans.save(value);
	}

	@Transactional
	public ExamSession addSession(String planId, ExamCommands.Session command) {
		return saveSession(planId, null, command);
	}

	@Transactional
	public ExamSession saveSession(
			String planId,
			String sessionId,
			ExamCommands.Session command) {
		ExamPlan plan = draft(planId);
		resourceLock.lockSemester(plan.getSemesterCode());
		if (!subjects.existsById(command.subjectId())) {
			throw new IllegalArgumentException("考试科目不存在");
		}
		if (command.examDate() == null || command.startTime() == null
				|| command.endTime() == null || !command.endTime().isAfter(command.startTime())
				|| command.examDate().isBefore(plan.getStartDate())
				|| command.examDate().isAfter(plan.getEndDate())) {
			throw new IllegalArgumentException("考试场次日期或时间无效");
		}
		ExamSession value = sessionId == null ? new ExamSession() : session(sessionId);
		if (sessionId != null && (!value.getPlanId().equals(planId)
				|| !rooms.findBySessionId(sessionId).isEmpty())) {
			throw new IllegalStateException("已有考场的场次不可直接改期，请先移除考场");
		}
		value.setPlanId(planId);
		value.setSubjectId(command.subjectId());
		value.setExamDate(command.examDate());
		value.setStartTime(command.startTime());
		value.setEndTime(command.endTime());
		return sessions.save(value);
	}

	@Transactional
	public void deletePlan(String planId) {
		resourceLock.lockSemester(draft(planId).getSemesterCode());
		if (!suspensionRequests.findByPlanIdOrderByCreateTimeDesc(planId).isEmpty()) {
			throw new IllegalStateException("计划已有占课审批历史，不能物理删除");
		}
		for (ExamSession session : sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(planId)) {
			deleteSession(planId, session.getId());
		}
		plans.deleteById(planId);
	}

	@Transactional
	public void deleteSession(String planId, String sessionId) {
		resourceLock.lockSemester(draft(planId).getSemesterCode());
		ExamSession session = session(sessionId);
		if (!session.getPlanId().equals(planId)) {
			throw new IllegalArgumentException("场次不属于该计划");
		}
		if (paperAnalysis.hasItems(sessionId)) {
			throw new IllegalStateException("请先移除该场次的试卷题目，再删除场次");
		}
		for (ExamRoom room : rooms.findBySessionId(sessionId)) {
			deleteRoom(sessionId, room.getId());
		}
		sessions.delete(session);
	}

	@Transactional
	public void deleteRoom(String sessionId, String roomId) {
		ExamSession session = session(sessionId);
		resourceLock.lockSemester(draft(session.getPlanId()).getSemesterCode());
		ExamRoom room = room(roomId);
		if (!room.getSessionId().equals(sessionId)) {
			throw new IllegalArgumentException("考场不属于该场次");
		}
		if (paperAnalysis.hasScoresForRoom(roomId)) {
			throw new IllegalStateException("考场已有逐题评分，不能删除考生座位");
		}
		candidates.deleteAll(candidates.findByRoomIdOrderBySeatNoAsc(roomId));
		assignments.deleteAll(assignments.findByRoomIdAndStatus(roomId, "ASSIGNED"));
		assignments.deleteAll(assignments.findByRoomIdAndStatus(roomId, "STANDBY"));
		rooms.delete(room);
	}

	@Transactional
	public ExamRoom addRoom(String sessionId, ExamCommands.Room command) {
		ExamSession session = session(sessionId);
		resourceLock.lockSemester(draft(session.getPlanId()).getSemesterCode());
		Classroom classroom = classrooms.findById(command.classroomId())
				.orElseThrow(() -> new IllegalArgumentException("教室不存在"));
		if (!Boolean.TRUE.equals(classroom.getEnabled())) {
			throw new IllegalStateException("教室已停用");
		}
		if (rooms.existsBySessionIdAndClassroomId(sessionId, command.classroomId())) {
			throw new IllegalStateException("该场次已使用此考场");
		}
		ExamRoom value = new ExamRoom();
		value.setSessionId(sessionId);
		value.setClassroomId(command.classroomId());
		value.setRequiredInvigilators(command.requiredInvigilators() == null
				? plan(session.getPlanId()).getBaseInvigilators()
				: command.requiredInvigilators());
		if (value.getRequiredInvigilators() < 1) {
			throw new IllegalArgumentException("监考人数至少为 1");
		}
		return rooms.save(value);
	}

	@Transactional
	public List<ExamCandidate> addCandidates(String roomId, ExamCommands.Candidates command) {
		ExamRoom room = room(roomId);
		ExamSession session = session(room.getSessionId());
		resourceLock.lockSemester(draft(session.getPlanId()).getSemesterCode());
		Classroom classroom = classrooms.findById(room.getClassroomId()).orElseThrow();
		List<ExamCandidate> result = new ArrayList<>();
		int seatNo = candidates.findByRoomIdOrderBySeatNoAsc(roomId).stream()
				.map(ExamCandidate::getSeatNo)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(0);
		if (command.studentIds() == null || command.studentIds().isEmpty()) {
			throw new IllegalArgumentException("请选择考生");
		}
		for (String studentId : new LinkedHashSet<>(command.studentIds())) {
			if (!students.existsById(studentId)) {
				throw new IllegalArgumentException("学生不存在：" + studentId);
			}
			if (candidates.existsByRoomIdAndStudentId(roomId, studentId)) {
				continue;
			}
			for (ExamCandidate existing : candidates.findByStudentId(studentId)) {
				ExamSession other = session(room(existing.getRoomId()).getSessionId());
				if (sameTime(session, other)) {
					throw new IllegalStateException("学生同一时间已有考试：" + studentId);
				}
			}
			ExamCandidate candidate = new ExamCandidate();
			candidate.setRoomId(roomId);
			candidate.setStudentId(studentId);
			candidate.setSeatNo(++seatNo);
			result.add(candidates.save(candidate));
		}
		if (seatNo > classroom.getCapacity()) {
			throw new IllegalStateException("考场人数超过教室容量");
		}
		ExamPlan plan = plan(session.getPlanId());
		int required = plan.getBaseInvigilators()
				+ Math.max(0, (seatNo - 1) / plan.getExtraStaffThreshold());
		room.setRequiredInvigilators(Math.max(room.getRequiredInvigilators(), required));
		return result;
	}

	@Transactional
	public List<ExamCandidate> addClassCandidates(
			String roomId,
			String administrativeClassId) {
		if (!administrativeClasses.existsById(administrativeClassId)) {
			throw new IllegalArgumentException("行政班不存在");
		}
		List<String> studentIds = students
				.findByAdministrativeClassId(administrativeClassId)
				.stream()
				.filter(item -> "ACTIVE".equals(item.getEnrollmentStatus()))
				.map(item -> item.getId())
				.toList();
		return addCandidates(roomId, new ExamCommands.Candidates(studentIds));
	}

	@Transactional
	public void removeCandidate(String roomId, String candidateId) {
		ExamRoom room = room(roomId);
		resourceLock.lockSemester(draft(session(room.getSessionId())
				.getPlanId()).getSemesterCode());
		ExamCandidate candidate = candidates.findById(candidateId)
				.orElseThrow(() -> new IllegalArgumentException("考生座位不存在"));
		if (!candidate.getRoomId().equals(roomId)) {
			throw new IllegalArgumentException("考生座位不属于该考场");
		}
		candidates.delete(candidate);
	}

	@Transactional
	public void unassign(String roomId, String assignmentId) {
		ExamRoom room = room(roomId);
		resourceLock.lockSemester(draft(session(room.getSessionId())
				.getPlanId()).getSemesterCode());
		ExamInvigilation assignment = assignment(assignmentId);
		if (!assignment.getRoomId().equals(roomId)) {
			throw new IllegalArgumentException("监考任务不属于该考场");
		}
		assignments.delete(assignment);
	}

	@Transactional(readOnly = true)
	public List<String> previewConflicts(String planId) {
		ExamPlan plan = plan(planId);
		List<String> conflicts = new ArrayList<>();
		for (ExamSession session : sessions
				.findByPlanIdOrderByExamDateAscStartTimeAsc(planId)) {
			if ("CANCELLED".equals(session.getStatus())) {
				continue;
			}
			for (ExamRoom room : rooms.findBySessionId(session.getId())) {
				conflicts.addAll(roomConflicts(room, session));
				for (ExamCandidate candidate : candidates
						.findByRoomIdOrderBySeatNoAsc(room.getId())) {
					for (ScheduleOccurrenceView occurrence : courseOccurrences(session)) {
						if (!overlaps(session, occurrence)) {
							continue;
						}
						boolean enrolled = teachingClassMembers
								.findByOfferingIdAndStudentId(
										occurrence.entry().offeringId(),
										candidate.getStudentId())
									.filter(member -> "ENROLLED".equals(
											member.getEnrollmentStatus()))
									.isPresent();
						if (enrolled) {
							conflicts.add("考生与课程冲突：" + candidate.getStudentId());
						}
					}
				}
			}
		}
		return conflicts.stream().distinct().toList();
	}

	/** 预览考试占用的真实上课发生日，禁止凭周课表模板直接批量停课。 */
	@Transactional(readOnly = true)
	public List<ScheduleOccurrenceView> suspensionImpacts(
			String planId,
			String scopeMode) {
		if (!Set.of("SCHOOL_WIDE", "AFFECTED_ONLY").contains(scopeMode)) {
			throw new IllegalArgumentException("考试占课范围无效");
		}
		ExamPlan plan = plan(planId);
		Map<String, ScheduleOccurrenceView> impacted = new LinkedHashMap<>();
		for (ExamSession session : sessions
				.findByPlanIdOrderByExamDateAscStartTimeAsc(planId)) {
			List<ExamRoom> examRooms = rooms.findBySessionId(session.getId());
			Set<String> classroomIds = examRooms.stream()
					.map(ExamRoom::getClassroomId)
					.collect(Collectors.toSet());
			Set<String> studentIds = examRooms.stream()
					.flatMap(room -> candidates
							.findByRoomIdOrderBySeatNoAsc(room.getId()).stream())
					.map(ExamCandidate::getStudentId)
					.collect(Collectors.toSet());
			Set<String> teacherIds = examRooms.stream()
					.flatMap(room -> assignments
							.findByRoomIdAndStatus(room.getId(), "ASSIGNED").stream())
					.map(ExamInvigilation::getTeacherId)
					.collect(Collectors.toSet());
			for (ScheduleOccurrenceView occurrence : courseOccurrences(session)) {
				if (!overlaps(session, occurrence)) {
					continue;
				}
				if ("AFFECTED_ONLY".equals(scopeMode)) {
					CourseOffering offering = offerings.findById(
							occurrence.entry().offeringId()).orElse(null);
					boolean affected = classroomIds.contains(
							occurrence.effectiveClassroomId())
							|| offering != null && teacherIds.contains(offering.getTeacherId())
							|| studentIds.stream().anyMatch(studentId ->
								teachingClassMembers.findByOfferingIdAndStudentId(
										occurrence.entry().offeringId(), studentId)
										.filter(member -> "ENROLLED".equals(
												member.getEnrollmentStatus()))
										.isPresent());
					if (!affected) {
						continue;
					}
				}
				if (!"SCHEDULED".equals(occurrence.occurrenceStatus())) {
					throw new IllegalStateException(
							"存在已调课或代课的课程，请先在教务中心处理："
									+ occurrence.entry().courseName());
				}
				impacted.putIfAbsent(
						occurrence.entry().id() + "@" + occurrence.date(), occurrence);
			}
		}
		return List.copyOf(impacted.values());
	}

	@Transactional(readOnly = true)
	public List<ExamCourseSuspensionRequest> suspensionRequests(String planId) {
		plan(planId);
		return suspensionRequests.findByPlanIdOrderByCreateTimeDesc(planId);
	}

	@Transactional(readOnly = true)
	public List<ExamCourseSuspensionItem> suspensionItems(String requestId) {
		if (!suspensionRequests.existsById(requestId)) {
			throw new IllegalArgumentException("考试占课审批单不存在");
		}
		return suspensionItems.findByRequestId(requestId);
	}

	@Transactional
	public ExamCourseSuspensionRequest requestSuspension(
			String planId,
			String scopeMode,
			String reason,
			String actor) {
		ExamPlan plan = draft(planId);
		resourceLock.lockSemester(plan.getSemesterCode());
		required(reason, "占课原因");
		if (suspensionRequests.existsByPlanIdAndStatus(planId, "PENDING")) {
			throw new IllegalStateException("该考试计划已有待审批的占课申请");
		}
		List<ScheduleOccurrenceView> impacts = suspensionImpacts(planId, scopeMode);
		if (impacts.isEmpty()) {
			throw new IllegalStateException("当前考试计划没有需要批量处理的课程冲突");
		}
		ExamCourseSuspensionRequest request = new ExamCourseSuspensionRequest();
		request.setPlanId(planId);
		request.setScopeMode(scopeMode);
		request.setReason(reason.trim());
		request.setRequestedBy(actor);
		request = suspensionRequests.save(request);
		for (ScheduleOccurrenceView impact : impacts) {
			ExamCourseSuspensionItem item = new ExamCourseSuspensionItem();
			item.setRequestId(request.getId());
			item.setSourceEntryId(impact.entry().id());
			item.setSourceDate(impact.date());
			suspensionItems.save(item);
		}
		audit.log(actor, "EDUCATION_EXAM_SUSPENSION_REQUEST",
				"planId=" + planId + ", requestId=" + request.getId()
						+ ", count=" + impacts.size());
		examNotifications.suspensionRequested(request.getId(), plan.getPlanName());
		return request;
	}

	/** 审批时重算影响范围；课表或考场变化必须重新申请，避免批准过期快照。 */
	@Transactional
	public ExamCourseSuspensionRequest decideSuspension(
			String requestId,
			boolean approve,
			String actor) {
		ExamCourseSuspensionRequest request = suspensionRequests.findById(requestId)
				.orElseThrow(() -> new IllegalArgumentException("考试占课审批单不存在"));
		ExamPlan plan = draft(request.getPlanId());
		resourceLock.lockSemester(plan.getSemesterCode());
		if (!"PENDING".equals(request.getStatus())) {
			throw new IllegalStateException("审批单已处理");
		}
		if (actor.equals(request.getRequestedBy())) {
			throw new AccessDeniedException("申请人不能审批自己的考试占课申请");
		}
		List<ExamCourseSuspensionItem> saved = suspensionItems
				.findByRequestId(requestId);
		if (approve) {
			Set<String> current = suspensionImpacts(
					request.getPlanId(), request.getScopeMode()).stream()
					.map(item -> item.entry().id() + "@" + item.date())
					.collect(Collectors.toSet());
			Set<String> submitted = saved.stream()
					.map(item -> item.getSourceEntryId() + "@" + item.getSourceDate())
					.collect(Collectors.toSet());
			if (!current.equals(submitted)) {
				throw new IllegalStateException("课程或考试安排已变化，请重新提交占课申请");
			}
			for (ExamCourseSuspensionItem item : saved) {
				ScheduleDateException exception = new ScheduleDateException();
				exception.setSemesterCode(plan.getSemesterCode());
				exception.setSourceEntryId(item.getSourceEntryId());
				exception.setSourceDate(item.getSourceDate());
				exception.setExceptionType("CANCEL");
				exception.setReason("考试占课：" + plan.getPlanName()
						+ "；" + request.getReason());
				ScheduleDateException created = occurrences.saveException(
						null, exception);
				item.setExceptionId(created.getId());
				suspensionItems.save(item);
			}
		}
		request.setStatus(approve ? "APPROVED" : "REJECTED");
		request.setDecidedBy(actor);
		request.setDecidedAt(LocalDateTime.now());
		ExamCourseSuspensionRequest decided = suspensionRequests.save(request);
		audit.log(actor, "EDUCATION_EXAM_SUSPENSION_DECISION",
				"requestId=" + requestId + ", status=" + decided.getStatus());
		examNotifications.suspensionDecided(
				requestId, request.getRequestedBy(), approve);
		return decided;
	}

	@Transactional(readOnly = true)
	public List<ExamTeacherSuggestion> availableTeachers(String roomId) {
		ExamRoom room = room(roomId);
		ExamSession session = session(room.getSessionId());
		String semesterCode = plan(session.getPlanId()).getSemesterCode();
		return teachers.findAllByOrderByTeacherNo().stream()
				.filter(item -> Boolean.TRUE.equals(item.getEnabled()))
				.filter(item -> teacherConflicts(
						item.getId(), session, null, roomId).isEmpty())
				.map(item -> new ExamTeacherSuggestion(
						item.getId(), item.getTeacherName(),
						assignments.findByTeacherIdAndStatus(item.getId(), "ASSIGNED")
								.stream()
								.filter(assignment -> semesterCode.equals(plan(
										session(room(assignment.getRoomId())
											.getSessionId()).getPlanId())
											.getSemesterCode()))
								.count()))
				.sorted(Comparator.comparingLong(
						ExamTeacherSuggestion::semesterDutyCount)
						.thenComparing(ExamTeacherSuggestion::teacherName))
				.toList();
	}

	/** 先按硬约束过滤，再以学期已分配次数最少优先；教务员仍可手工调整。 */
	@Transactional
	public List<ExamInvigilation> autoAssign(String planId) {
		resourceLock.lockSemester(draft(planId).getSemesterCode());
		List<ExamInvigilation> created = new ArrayList<>();
		for (ExamSession session : sessions
				.findByPlanIdOrderByExamDateAscStartTimeAsc(planId)) {
			for (ExamRoom room : rooms.findBySessionId(session.getId())) {
				if (candidates.findByRoomIdOrderBySeatNoAsc(room.getId()).isEmpty()) {
					throw new IllegalStateException("请先为每个考场安排考生");
				}
				while (assignments.findByRoomIdAndStatus(room.getId(), "ASSIGNED")
						.size() < room.getRequiredInvigilators()) {
					List<ExamInvigilation> current = assignments
							.findByRoomIdAndStatus(room.getId(), "ASSIGNED");
					boolean hasChief = current.stream()
							.anyMatch(item -> "CHIEF".equals(item.getDutyRole()));
					ExamTeacherSuggestion teacher = availableTeachers(room.getId())
							.stream()
							.findFirst()
							.orElseThrow(() -> new IllegalStateException(
									"可用教师不足，无法完成自动监考排班"));
					created.add(assign(room.getId(), new ExamCommands.Assignment(
							teacher.id(), hasChief ? "ASSISTANT" : "CHIEF")));
				}
			}
		}
		return created;
	}

	@Transactional
	public ExamInvigilation assign(String roomId, ExamCommands.Assignment command) {
		ExamRoom room = room(roomId);
		ExamSession session = session(room.getSessionId());
		resourceLock.lockSemester(draft(session.getPlanId()).getSemesterCode());
		if (!Set.of("CHIEF", "ASSISTANT").contains(command.dutyRole())) {
			throw new IllegalArgumentException("监考岗位无效");
		}
		assertTeacherAvailable(command.teacherId(), session, null, roomId);
		if (assignments.findByRoomIdAndStatus(roomId, "ASSIGNED").size()
				>= room.getRequiredInvigilators()) {
			throw new IllegalStateException("考场监考人数已满");
		}
		if ("CHIEF".equals(command.dutyRole())
				&& assignments.findByRoomIdAndStatus(roomId, "ASSIGNED").stream()
						.anyMatch(item -> "CHIEF".equals(item.getDutyRole()))) {
			throw new IllegalStateException("考场只能有一名主监考");
		}
		ExamInvigilation value = new ExamInvigilation();
		value.setRoomId(roomId);
		value.setTeacherId(command.teacherId());
		value.setDutyRole(command.dutyRole());
		return assignments.save(value);
	}

	/** 发布时重新校验所有资源；冲突课程须先在调停课模块中处理。 */
	@Transactional
	public ExamPlan publish(String planId, String actor) {
		ExamPlan plan = draft(planId);
		resourceLock.lockSemester(plan.getSemesterCode());
		List<String> conflicts = previewConflicts(planId);
		if (!conflicts.isEmpty()) {
			throw new IllegalStateException(String.join("；", conflicts));
		}
		List<ExamSession> planSessions = sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(planId);
		if (planSessions.isEmpty()) {
			throw new IllegalStateException("考试计划尚无场次");
		}
		for (ExamSession session : planSessions) {
			List<ExamRoom> examRooms = rooms.findBySessionId(session.getId());
			if (examRooms.isEmpty()) {
				throw new IllegalStateException("场次尚未分配考场");
			}
			for (ExamRoom room : examRooms) {
				if (candidates.findByRoomIdOrderBySeatNoAsc(room.getId()).isEmpty()) {
					throw new IllegalStateException("考场尚未分配考生");
				}
				List<String> roomIssues = roomConflicts(room, session);
				if (!roomIssues.isEmpty()) {
					throw new IllegalStateException(String.join("；", roomIssues));
				}
				List<ExamInvigilation> staff = assignments.findByRoomIdAndStatus(
						room.getId(), "ASSIGNED");
				if (staff.size() < room.getRequiredInvigilators()
						|| staff.stream().noneMatch(item -> "CHIEF".equals(item.getDutyRole()))) {
					throw new IllegalStateException("考场监考人数不足或缺少主监考");
				}
				for (ExamInvigilation item : staff) {
					assertTeacherAvailable(
							item.getTeacherId(), session, item.getId(), room.getId());
				}
				for (ExamInvigilation standby : assignments
						.findByRoomIdAndStatus(room.getId(), "STANDBY")) {
					assertTeacherAvailable(
							standby.getTeacherId(), session,
							standby.getId(), room.getId());
				}
				room.setStatus("PUBLISHED");
			}
			session.setStatus("PUBLISHED");
		}
		plan.setStatus("PUBLISHED");
		plan.setPublishedVersion(1);
		ExamPlan published = plans.save(plan);
		audit.log(actor, "EDUCATION_EXAM_PUBLISH",
				"planId=" + planId + ", semester=" + plan.getSemesterCode());
		examNotifications.planPublished(published);
		return published;
	}

	@Transactional(readOnly = true)
	public List<ExamPublishedChange> publishedChanges(String planId) {
		plan(planId);
		return publishedChanges.findByPlanIdOrderByCreateTimeDesc(planId);
	}

	@Transactional
	public ExamPublishedChange requestPublishedChange(
			String planId,
			ExamCommands.PublishedChange command,
			String actor) {
		ExamPlan plan = plan(planId);
		resourceLock.lockSemester(plan.getSemesterCode());
		if (!"PUBLISHED".equals(plan.getStatus())) {
			throw new IllegalStateException("仅已发布考试允许申请变更");
		}
		if (publishedChanges.existsByPlanIdAndStatus(planId, "PENDING")) {
			throw new IllegalStateException("该计划已有待处理的发布后变更");
		}
		required(command.reason(), "变更原因");
		if (!Set.of("RESCHEDULE", "ROOM_CHANGE", "CANCEL_SESSION", "CANCEL_PLAN")
				.contains(command.changeType())) {
			throw new IllegalArgumentException("不支持的考试变更类型");
		}
		ExamPublishedChange value = new ExamPublishedChange();
		value.setPlanId(planId);
		value.setChangeType(command.changeType());
		value.setBasePlanVersion(plan.getPublishedVersion());
		value.setRequestedBy(actor);
		value.setReason(command.reason().trim());
		if (!"CANCEL_PLAN".equals(command.changeType())) {
			ExamSession target = session(command.sessionId());
			if (!planId.equals(target.getPlanId())
					|| !"PUBLISHED".equals(target.getStatus())) {
				throw new IllegalArgumentException("目标场次不属于当前已发布计划");
			}
			value.setSessionId(target.getId());
			value.setOldExamDate(target.getExamDate());
			value.setOldStartTime(target.getStartTime());
			value.setOldEndTime(target.getEndTime());
			if ("RESCHEDULE".equals(command.changeType())) {
				if (command.newExamDate() == null || command.newStartTime() == null
						|| command.newEndTime() == null
						|| !command.newEndTime().isAfter(command.newStartTime())) {
					throw new IllegalArgumentException("请填写新的考试日期和有效时间");
				}
				value.setNewExamDate(command.newExamDate());
				value.setNewStartTime(command.newStartTime());
				value.setNewEndTime(command.newEndTime());
			}
			if ("ROOM_CHANGE".equals(command.changeType())) {
				ExamRoom targetRoom = room(command.roomId());
				if (!targetRoom.getSessionId().equals(target.getId())) {
					throw new IllegalArgumentException("考场不属于目标场次");
				}
				if (command.newClassroomId() == null
						|| !classrooms.existsById(command.newClassroomId())) {
					throw new IllegalArgumentException("新教室不存在");
				}
				value.setRoomId(targetRoom.getId());
				value.setOldClassroomId(targetRoom.getClassroomId());
				value.setNewClassroomId(command.newClassroomId());
			}
		}
		ExamPublishedChange requested = publishedChanges.save(value);
		audit.log(actor, "EDUCATION_EXAM_CHANGE_REQUEST",
				"planId=" + planId + ", changeId=" + requested.getId()
						+ ", type=" + requested.getChangeType());
		examNotifications.publishedChangeRequested(
				requested.getId(), plan.getPlanName());
		return requested;
	}

	/** 版本门禁和资源锁保证审批所见的快照仍然有效。 */
	@Transactional
	public ExamPublishedChange decidePublishedChange(
			String changeId,
			boolean approve,
			String actor) {
		ExamPublishedChange change = publishedChanges.findById(changeId)
				.orElseThrow(() -> new IllegalArgumentException("考试变更申请不存在"));
		ExamPlan plan = plan(change.getPlanId());
		resourceLock.lockSemester(plan.getSemesterCode());
		if (!"PENDING".equals(change.getStatus())) {
			throw new IllegalStateException("变更申请已处理");
		}
		if (actor.equals(change.getRequestedBy())) {
			throw new AccessDeniedException("申请人不能审批自己的考试变更");
		}
		if (approve) {
			if (!"PUBLISHED".equals(plan.getStatus())
					|| !Objects.equals(plan.getPublishedVersion(),
							change.getBasePlanVersion())) {
				throw new IllegalStateException("考试计划版本已变化，请重新发起变更");
			}
			applyPublishedChange(change, plan);
			plan.setPublishedVersion(plan.getPublishedVersion() + 1);
			change.setAppliedPlanVersion(plan.getPublishedVersion());
			plans.save(plan);
		}
		change.setStatus(approve ? "APPROVED" : "REJECTED");
		change.setDecidedBy(actor);
		change.setDecidedAt(LocalDateTime.now());
		ExamPublishedChange decided = publishedChanges.save(change);
		audit.log(actor, "EDUCATION_EXAM_CHANGE_DECISION",
				"changeId=" + changeId + ", status=" + decided.getStatus()
						+ ", version=" + decided.getAppliedPlanVersion());
		examNotifications.publishedChangeDecided(
				decided, plan.getPlanName());
		return decided;
	}

	private void applyPublishedChange(ExamPublishedChange change, ExamPlan plan) {
		String type = change.getChangeType();
		if ("CANCEL_PLAN".equals(type)) {
			cancelPublishedPlan(plan);
			return;
		}
		ExamSession target = session(change.getSessionId());
		if (!Objects.equals(target.getExamDate(), change.getOldExamDate())
				|| !Objects.equals(target.getStartTime(), change.getOldStartTime())
				|| !Objects.equals(target.getEndTime(), change.getOldEndTime())) {
			throw new IllegalStateException("场次已发生变化，请重新申请");
		}
		if ("CANCEL_SESSION".equals(type)) {
			ensureNoApprovedSuspensions(plan.getId());
			cancelPublishedSession(target);
			if (sessions.findByPlanIdOrderByExamDateAscStartTimeAsc(plan.getId())
					.stream().noneMatch(item -> "PUBLISHED".equals(item.getStatus()))) {
				plan.setStatus("CANCELLED");
			}
			return;
		}
		ensureNoApprovedSuspensions(plan.getId());
		if ("RESCHEDULE".equals(type)) {
			AcademicTerm term = terms.findByTermCode(plan.getSemesterCode())
					.orElseThrow();
			if (change.getNewExamDate().isBefore(term.getStartDate())
					|| change.getNewExamDate().isAfter(term.getEndDate())) {
				throw new IllegalArgumentException("新考试日期不在学期内");
			}
			target.setExamDate(change.getNewExamDate());
			target.setStartTime(change.getNewStartTime());
			target.setEndTime(change.getNewEndTime());
			sessions.save(target);
			List<ExamSession> active = sessions
					.findByPlanIdOrderByExamDateAscStartTimeAsc(plan.getId()).stream()
					.filter(item -> "PUBLISHED".equals(item.getStatus()))
					.toList();
			plan.setStartDate(active.stream().map(ExamSession::getExamDate)
					.min(LocalDate::compareTo).orElseThrow());
			plan.setEndDate(active.stream().map(ExamSession::getExamDate)
					.max(LocalDate::compareTo).orElseThrow());
		} else if ("ROOM_CHANGE".equals(type)) {
			ExamRoom targetRoom = room(change.getRoomId());
			if (!targetRoom.getSessionId().equals(target.getId())
					|| !Objects.equals(targetRoom.getClassroomId(),
							change.getOldClassroomId())) {
				throw new IllegalStateException("考场已发生变化，请重新申请");
			}
			Classroom replacement = classrooms.findById(change.getNewClassroomId())
					.orElseThrow(() -> new IllegalArgumentException("新教室不存在"));
			if (!Boolean.TRUE.equals(replacement.getEnabled())
					|| replacement.getCapacity() < candidates
							.findByRoomIdOrderBySeatNoAsc(targetRoom.getId()).size()
					|| rooms.findBySessionId(target.getId()).stream()
							.anyMatch(item -> !item.getId().equals(targetRoom.getId())
									&& item.getClassroomId().equals(replacement.getId()))) {
				throw new IllegalStateException("新考场不可用、容量不足或被本场次占用");
			}
			targetRoom.setClassroomId(replacement.getId());
			rooms.save(targetRoom);
		}
		validatePublishedConfiguration(plan.getId());
	}

	private void validatePublishedConfiguration(String planId) {
		List<String> conflicts = previewConflicts(planId);
		if (!conflicts.isEmpty()) {
			throw new IllegalStateException(String.join("；", conflicts));
		}
		for (ExamSession active : sessions
				.findByPlanIdOrderByExamDateAscStartTimeAsc(planId)) {
			if (!"PUBLISHED".equals(active.getStatus())) {
				continue;
			}
			for (ExamRoom examRoom : rooms.findBySessionId(active.getId())) {
				List<String> roomIssues = roomConflicts(examRoom, active);
				if (!roomIssues.isEmpty()) {
					throw new IllegalStateException(String.join("；", roomIssues));
				}
				for (ExamInvigilation duty : assignments
						.findByRoomIdAndStatus(examRoom.getId(), "ASSIGNED")) {
					assertTeacherAvailable(
							duty.getTeacherId(), active, duty.getId(), examRoom.getId());
				}
			}
		}
	}

	private void cancelPublishedSession(ExamSession target) {
		for (ExamRoom examRoom : rooms.findBySessionId(target.getId())) {
			if (paperAnalysis.hasScoresForRoom(examRoom.getId())) {
				throw new IllegalStateException("考场已有逐题评分，不能直接取消考试");
			}
			examRoom.setStatus("CANCELLED");
			for (ExamInvigilation duty : assignments
					.findByRoomIdAndStatus(examRoom.getId(), "ASSIGNED")) {
				duty.setStatus("CANCELLED");
			}
			for (ExamInvigilation standby : assignments
					.findByRoomIdAndStatus(examRoom.getId(), "STANDBY")) {
				standby.setStatus("CANCELLED");
			}
		}
		target.setStatus("CANCELLED");
	}

	private void cancelPublishedPlan(ExamPlan plan) {
		for (ExamSession active : sessions
				.findByPlanIdOrderByExamDateAscStartTimeAsc(plan.getId())) {
			if ("PUBLISHED".equals(active.getStatus())) {
				cancelPublishedSession(active);
			}
		}
		for (ExamCourseSuspensionRequest suspension : suspensionRequests
				.findByPlanIdOrderByCreateTimeDesc(plan.getId())) {
			if (!"APPROVED".equals(suspension.getStatus())) {
				continue;
			}
			for (ExamCourseSuspensionItem item : suspensionItems
					.findByRequestId(suspension.getId())) {
				if (item.getExceptionId() == null) {
					continue;
				}
				occurrences.cancelException(item.getExceptionId());
				ScheduleEntry entry = scheduleEntries.findById(item.getSourceEntryId())
						.orElseThrow(() -> new IllegalStateException(
								"停课源课表已删除，不能自动恢复"));
				CourseOffering offering = offerings.findById(entry.getOfferingId())
						.orElseThrow();
				String campusId = classrooms.findById(entry.getClassroomId())
						.orElseThrow().getCampusId();
				Set<String> enrolled = teachingClassMembers
						.findByOfferingIdOrderByCreateTime(entry.getOfferingId())
						.stream()
						.filter(member -> "ENROLLED".equals(member.getEnrollmentStatus()))
						.map(member -> member.getStudentId())
						.collect(Collectors.toSet());
				examReservations.assertDatedCourseAvailable(
						plan.getSemesterCode(), campusId, item.getSourceDate(),
						entry.getPeriodNo(), entry.getDurationPeriods(),
						entry.getClassroomId(), offering.getTeacherId(), enrolled);
			}
			suspension.setStatus("RESTORED");
		}
		plan.setStatus("CANCELLED");
	}

	private void ensureNoApprovedSuspensions(String planId) {
		if (suspensionRequests.existsByPlanIdAndStatus(planId, "APPROVED")) {
			throw new IllegalStateException(
					"该计划已有批量停课，须整体取消并恢复课程后重新规划考试");
		}
	}

	@Transactional
	public ExamInvigilationChange requestChange(
			String assignmentId,
			ExamCommands.Change command,
			String username,
			boolean emergency) {
		ExamInvigilation assignment = assignment(assignmentId);
		if (!"PUBLISHED".equals(session(room(assignment.getRoomId())
				.getSessionId()).getStatus())) {
			throw new IllegalStateException("仅已发布的监考任务允许申请调换");
		}
		var scope = dataScopes.resolve(username);
		if (!scope.fullAccess()
				&& !scope.teacherIds().contains(assignment.getTeacherId())) {
			throw new AccessDeniedException("只能申请调换本人监考任务");
		}
		if (changes.existsByAssignmentIdAndStatus(assignmentId, "PENDING")) {
			throw new IllegalStateException("已有待审核的监考调换申请");
		}
		required(command.reason(), "调换原因");
		ExamInvigilationChange value = new ExamInvigilationChange();
		value.setAssignmentId(assignment.getId());
		value.setProposedTeacherId(command.proposedTeacherId());
		value.setReason(command.reason());
		value.setRequestedBy(username);
		value.setEmergency(emergency);
		ExamInvigilationChange created = changes.save(value);
		audit.log(username, "EDUCATION_INVIGILATION_CHANGE_REQUEST",
				"assignmentId=" + assignmentId + ", changeId=" + created.getId());
		examNotifications.changeRequested(created.getId(), created.getReason());
		return created;
	}

	/** 应急替补在一个事务中完成申请、复检和调换，失败时不遗留待审记录。 */
	@Transactional
	public ExamInvigilationChange emergencyChange(
			String assignmentId,
			ExamCommands.Change command,
			String username) {
		ExamInvigilationChange change = requestChange(
				assignmentId, command, username, true);
		return decideChange(
				change.getId(),
				new ExamCommands.Decision(true, command.proposedTeacherId()),
				username);
	}

	@Transactional
	public ExamInvigilationChange decideChange(
			String changeId,
			ExamCommands.Decision command,
			String username) {
		ExamInvigilationChange change = changes.findById(changeId)
				.orElseThrow(() -> new IllegalArgumentException("申请不存在"));
		if (!"PENDING".equals(change.getStatus())) {
			throw new IllegalStateException("申请已处理");
		}
		if (command.approve()) {
			ExamInvigilation old = assignment(change.getAssignmentId());
			resourceLock.lockSemester(plan(session(room(old.getRoomId())
					.getSessionId()).getPlanId()).getSemesterCode());
			String teacherId = command.replacementTeacherId() == null
					? change.getProposedTeacherId() : command.replacementTeacherId();
			ExamRoom room = room(old.getRoomId());
			assertTeacherAvailable(
					teacherId, session(room.getSessionId()), old.getId(), room.getId());
			old.setStatus("REPLACED");
			ExamInvigilation replacement = new ExamInvigilation();
			replacement.setRoomId(room.getId());
			replacement.setTeacherId(teacherId);
			replacement.setDutyRole(old.getDutyRole());
			assignments.save(replacement);
			old.setReplacedById(replacement.getId());
			change.setStatus("APPROVED");
		} else {
			change.setStatus("REJECTED");
		}
		change.setDecidedBy(username);
		change.setDecidedAt(LocalDateTime.now());
		ExamInvigilationChange decided = changes.save(change);
		audit.log(username, "EDUCATION_INVIGILATION_CHANGE_DECISION",
				"changeId=" + changeId + ", status=" + decided.getStatus());
		ExamInvigilation previous = assignments.findById(change.getAssignmentId())
				.orElseThrow();
		examNotifications.changeDecided(
				decided.getId(), decided.getRequestedBy(),
				command.approve()
						? assignments.findById(previous.getReplacedById())
								.orElseThrow().getTeacherId()
						: null,
				command.approve());
		return decided;
	}

	private List<String> roomConflicts(ExamRoom room, ExamSession session) {
		List<String> conflicts = new ArrayList<>();
		for (ExamSession other : overlapping(session)) {
			if (!other.getId().equals(session.getId())
					&& !"CANCELLED".equals(other.getStatus())
					&& ("PUBLISHED".equals(other.getStatus())
							|| other.getPlanId().equals(session.getPlanId()))
					&& rooms.findBySessionId(other.getId()).stream()
							.anyMatch(item -> room.getClassroomId().equals(item.getClassroomId()))) {
				conflicts.add("考场与其他考试场次冲突");
			}
		}
		for (ScheduleOccurrenceView occurrence : courseOccurrences(session)) {
			if (room.getClassroomId().equals(occurrence.effectiveClassroomId())
					&& overlaps(session, occurrence)) {
				conflicts.add("考场与课程冲突：" + occurrence.entry().courseName());
			}
		}
		return conflicts;
	}

	private void assertTeacherAvailable(
			String teacherId,
			ExamSession session,
			String excludeAssignmentId,
			String roomId) {
		if (teacherId == null || teachers.findById(teacherId)
				.filter(item -> Boolean.TRUE.equals(item.getEnabled())).isEmpty()) {
			throw new IllegalArgumentException("监考教师不存在或已停用");
		}
		List<String> conflicts = teacherConflicts(
				teacherId, session, excludeAssignmentId, roomId);
		if (!conflicts.isEmpty()) {
			throw new IllegalStateException(String.join("；", conflicts));
		}
	}

	private List<String> teacherConflicts(
			String teacherId,
			ExamSession session,
			String excludeAssignmentId,
			String roomId) {
		List<String> conflicts = new ArrayList<>();
		if (Boolean.TRUE.equals(plan(session.getPlanId())
				.getRequireSubjectQualification())
				&& !qualifications.existsByTeacherIdAndSubjectId(
						teacherId, session.getSubjectId())) {
			conflicts.add("教师未具备该考试科目的监考资格");
		}
		if (leaveRecords
				.existsByApplicantTypeAndApplicantIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
						"TEACHER", teacherId, "APPROVED",
						session.getExamDate(), session.getExamDate())) {
			conflicts.add("教师考试当天已有审批通过的请假");
		}
		Map<String, CourseOffering> byId = offerings
				.findBySemesterCodeOrderByOfferingCode(plan(session.getPlanId()).getSemesterCode())
				.stream()
				.collect(Collectors.toMap(CourseOffering::getId, Function.identity()));
		for (ScheduleOccurrenceView occurrence : courseOccurrences(session)) {
			CourseOffering offering = byId.get(occurrence.entry().offeringId());
			if (offering != null && teacherId.equals(offering.getTeacherId())
					&& overlaps(session, occurrence)) {
				conflicts.add("教师与授课冲突");
			}
		}
		List<ExamSession> sameDayDuties = new ArrayList<>();
		String targetCampusId = classrooms.findById(room(roomId).getClassroomId())
				.orElseThrow().getCampusId();
		for (ExamInvigilation assignment : assignments.findByTeacherIdAndStatusIn(
				teacherId, List.of("ASSIGNED", "STANDBY"))) {
			if (assignment.getId().equals(excludeAssignmentId)) {
				continue;
			}
			ExamSession other = session(room(assignment.getRoomId()).getSessionId());
			if (sameTime(session, other)) {
				conflicts.add("教师已有监考任务");
			}
			if (session.getExamDate().equals(other.getExamDate())) {
				sameDayDuties.add(other);
				String otherCampusId = classrooms.findById(
						room(assignment.getRoomId()).getClassroomId())
						.orElseThrow().getCampusId();
				if (!Objects.equals(targetCampusId, otherCampusId)
						&& !sameTime(session, other)) {
					long gap = session.getStartTime().isAfter(other.getEndTime())
							? Duration.between(other.getEndTime(), session.getStartTime()).toMinutes()
							: Duration.between(session.getEndTime(), other.getStartTime()).toMinutes();
					if (gap < plan(session.getPlanId()).getCampusTravelMinutes()) {
						conflicts.add("跨校区监考间隔不足");
					}
				}
			}
		}
		if (consecutiveDutyCount(session, sameDayDuties)
				> plan(session.getPlanId()).getMaxConsecutiveDuties()) {
			conflicts.add("超过教师连续监考场次上限");
		}
		AcademicTerm term = terms.findByTermCode(
				plan(session.getPlanId()).getSemesterCode()).orElseThrow();
		String campusId = classrooms.findById(room(roomId).getClassroomId())
				.orElseThrow().getCampusId();
		for (var constraint : teacherConstraints
				.findBySemesterCodeAndTeacherId(term.getTermCode(), teacherId)) {
			if (!"FORBIDDEN".equals(constraint.getConstraintType())
					|| constraint.getDayOfWeek()
							!= session.getExamDate().getDayOfWeek().getValue()) {
				continue;
			}
			for (BellSchedule bell : bellSchedules
					.findByAcademicTermIdOrderByScheduleName(term.getId())) {
				if (campusId != null && !campusId.equals(bell.getCampusId())) {
					continue;
				}
				for (BellPeriod period : bellPeriods
						.findByBellScheduleIdOrderByPeriodNo(bell.getId())) {
					if (period.getPeriodNo().equals(constraint.getPeriodNo())
							&& session.getStartTime().isBefore(period.getEndTime())
							&& period.getStartTime().isBefore(session.getEndTime())) {
						conflicts.add("教师命中禁排时间");
					}
				}
			}
		}
		if (!Boolean.TRUE.equals(plan(session.getPlanId())
				.getAllowOwnClassInvigilation())) {
			Set<String> teachingOfferings = offerings
					.findByTeacherIdOrderByOfferingCode(teacherId)
					.stream()
					.filter(item -> item.getSemesterCode().equals(
							plan(session.getPlanId()).getSemesterCode()))
					.map(CourseOffering::getId)
					.collect(Collectors.toSet());
			for (ExamCandidate candidate : candidates
					.findByRoomIdOrderBySeatNoAsc(roomId)) {
				boolean ownStudent = teachingClassMembers
						.findByStudentIdAndEnrollmentStatus(
								candidate.getStudentId(), "ENROLLED")
						.stream()
						.anyMatch(member -> teachingOfferings.contains(
								member.getOfferingId()));
				if (ownStudent) {
					conflicts.add("教师不得监考本人授课学生");
					break;
				}
			}
		}
		return conflicts;
	}

	@Transactional(readOnly = true)
	public List<ExamTeacherQualification> teacherQualifications(String teacherId) {
		if (!teachers.existsById(teacherId)) {
			throw new IllegalArgumentException("教师不存在");
		}
		return qualifications.findByTeacherId(teacherId);
	}

	@Transactional
	public ExamTeacherQualification addTeacherQualification(
			String teacherId,
			String subjectId) {
		if (!teachers.existsById(teacherId) || !subjects.existsById(subjectId)) {
			throw new IllegalArgumentException("教师或科目不存在");
		}
		return qualifications.findByTeacherIdAndSubjectId(teacherId, subjectId)
				.orElseGet(() -> {
					ExamTeacherQualification value = new ExamTeacherQualification();
					value.setTeacherId(teacherId);
					value.setSubjectId(subjectId);
					return qualifications.save(value);
				});
	}

	@Transactional
	public void removeTeacherQualification(String teacherId, String subjectId) {
		qualifications.findByTeacherIdAndSubjectId(teacherId, subjectId)
				.ifPresent(qualifications::delete);
	}

	/** 间隔不超过 30 分钟的相邻场次视为连续监考。 */
	private int consecutiveDutyCount(ExamSession target, List<ExamSession> duties) {
		List<ExamSession> ordered = new ArrayList<>(duties);
		ordered.add(target);
		ordered.sort(Comparator.comparing(ExamSession::getStartTime));
		int currentCount = 0;
		int targetCount = 1;
		LocalTime previousEnd = null;
		boolean includesTarget = false;
		for (ExamSession duty : ordered) {
			boolean continuous = previousEnd != null
					&& Duration.between(previousEnd, duty.getStartTime()).toMinutes() <= 30;
			if (!continuous) {
				currentCount = 0;
				includesTarget = false;
			}
			currentCount++;
			includesTarget |= duty == target;
			if (includesTarget) {
				targetCount = currentCount;
			}
			previousEnd = duty.getEndTime();
		}
		return targetCount;
	}

	private List<ExamSession> overlapping(ExamSession session) {
		return sessions.findByExamDateAndStartTimeLessThanAndEndTimeGreaterThan(
				session.getExamDate(), session.getEndTime(), session.getStartTime());
	}

	private boolean sameTime(ExamSession left, ExamSession right) {
		return left.getExamDate().equals(right.getExamDate())
				&& left.getStartTime().isBefore(right.getEndTime())
				&& right.getStartTime().isBefore(left.getEndTime());
	}

	private List<ScheduleOccurrenceView> courseOccurrences(ExamSession session) {
		return occurrences.occurrences(
				plan(session.getPlanId()).getSemesterCode(), session.getExamDate())
				.stream()
				.filter(item -> !Set.of("CANCELLED", "MOVED_OUT")
						.contains(item.occurrenceStatus()))
				.toList();
	}

	private boolean overlaps(ExamSession session, ScheduleOccurrenceView occurrence) {
		ExamPlan plan = plan(session.getPlanId());
		AcademicTerm term = terms.findByTermCode(plan.getSemesterCode()).orElseThrow();
		List<BellSchedule> scheduleOptions = bellSchedules
				.findByAcademicTermIdOrderByScheduleName(term.getId());
		String occurrenceClassroomId = occurrence.effectiveClassroomId() == null
				? occurrence.entry().classroomId()
				: occurrence.effectiveClassroomId();
		String campusId = classrooms.findById(occurrenceClassroomId)
				.orElseThrow(() -> new IllegalStateException(
						"日期课表引用的教室不存在"))
				.getCampusId();
		scheduleOptions = scheduleOptions.stream()
				.filter(item -> campusId == null
						|| campusId.equals(item.getCampusId()))
				.toList();
		if (scheduleOptions.isEmpty()) {
			throw new IllegalStateException("学期没有作息时间，无法校验考试与课程冲突");
		}
		int first = occurrence.effectivePeriodNo();
		int last = first + Math.max(1, occurrence.entry().durationPeriods()) - 1;
		for (BellSchedule bell : scheduleOptions) {
			Map<Integer, BellPeriod> periods = bellPeriods
					.findByBellScheduleIdOrderByPeriodNo(bell.getId())
					.stream()
					.collect(Collectors.toMap(BellPeriod::getPeriodNo, Function.identity()));
			BellPeriod start = periods.get(first);
			BellPeriod end = periods.get(last);
			if (start == null || end == null) {
				throw new IllegalStateException("课程节次缺少作息时间，无法校验考试冲突");
			}
			if (session.getStartTime().isBefore(end.getEndTime())
					&& start.getStartTime().isBefore(session.getEndTime())) {
				return true;
			}
		}
		return false;
	}

	private ExamPlan plan(String id) {
		return plans.findById(id).orElseThrow(() -> new IllegalArgumentException("考试计划不存在"));
	}

	private ExamPlan draft(String id) {
		ExamPlan plan = plan(id);
		if (!"DRAFT".equals(plan.getStatus())) {
			throw new IllegalStateException("只有草稿考试计划可以修改");
		}
		return plan;
	}

	private ExamSession session(String id) {
		return sessions.findById(id).orElseThrow(() -> new IllegalArgumentException("考试场次不存在"));
	}

	private ExamRoom room(String id) {
		return rooms.findById(id).orElseThrow(() -> new IllegalArgumentException("考场不存在"));
	}

	private ExamInvigilation assignment(String id) {
		return assignments.findById(id)
				.filter(item -> "ASSIGNED".equals(item.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("监考任务不存在或已调换"));
	}

	private void required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "不能为空");
		}
	}
}
