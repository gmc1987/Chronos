package com.chronos.education.scheduling.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ExamCandidateRepository;
import com.chronos.education.scheduling.dao.ExamInvigilationChangeRepository;
import com.chronos.education.scheduling.dao.ExamInvigilationRepository;
import com.chronos.education.scheduling.dao.ExamPlanRepository;
import com.chronos.education.scheduling.dao.ExamRoomRepository;
import com.chronos.education.scheduling.dao.ExamSessionRepository;
import com.chronos.education.scheduling.dao.StudentProfileRepository;
import com.chronos.education.scheduling.dao.SubjectRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ExamCandidate;
import com.chronos.education.scheduling.model.ExamCommands;
import com.chronos.education.scheduling.model.ExamInvigilation;
import com.chronos.education.scheduling.model.ExamInvigilationChange;
import com.chronos.education.scheduling.model.ExamPlan;
import com.chronos.education.scheduling.model.ExamRoom;
import com.chronos.education.scheduling.model.ExamSession;
import com.chronos.education.scheduling.model.ScheduleOccurrenceView;
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
	private final TeacherAcademicProfileRepository teachers;
	private final CourseOfferingRepository offerings;
	private final BellScheduleRepository bellSchedules;
	private final BellPeriodRepository bellPeriods;
	private final ScheduleOccurrenceService occurrences;

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
	public List<ExamInvigilationChange> pendingChanges() {
		return changes.findByStatusOrderByCreateTimeDesc("PENDING");
	}

	@Transactional
	public ExamPlan savePlan(String id, ExamCommands.Plan command) {
		required(command.semesterCode(), "学期");
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
		value.setSemesterCode(command.semesterCode());
		value.setPlanName(command.planName());
		value.setExamType(command.examType());
		value.setStartDate(command.startDate());
		value.setEndDate(command.endDate());
		value.setRuleJson(command.ruleJson());
		return plans.save(value);
	}

	@Transactional
	public ExamSession addSession(String planId, ExamCommands.Session command) {
		ExamPlan plan = draft(planId);
		if (!subjects.existsById(command.subjectId())) {
			throw new IllegalArgumentException("考试科目不存在");
		}
		if (command.examDate() == null || command.startTime() == null
				|| command.endTime() == null || !command.endTime().isAfter(command.startTime())
				|| command.examDate().isBefore(plan.getStartDate())
				|| command.examDate().isAfter(plan.getEndDate())) {
			throw new IllegalArgumentException("考试场次日期或时间无效");
		}
		ExamSession value = new ExamSession();
		value.setPlanId(planId);
		value.setSubjectId(command.subjectId());
		value.setExamDate(command.examDate());
		value.setStartTime(command.startTime());
		value.setEndTime(command.endTime());
		return sessions.save(value);
	}

	@Transactional
	public ExamRoom addRoom(String sessionId, ExamCommands.Room command) {
		ExamSession session = session(sessionId);
		draft(session.getPlanId());
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
				? 2 : command.requiredInvigilators());
		if (value.getRequiredInvigilators() < 1) {
			throw new IllegalArgumentException("监考人数至少为 1");
		}
		return rooms.save(value);
	}

	@Transactional
	public List<ExamCandidate> addCandidates(String roomId, ExamCommands.Candidates command) {
		ExamRoom room = room(roomId);
		ExamSession session = session(room.getSessionId());
		draft(session.getPlanId());
		Classroom classroom = classrooms.findById(room.getClassroomId()).orElseThrow();
		List<ExamCandidate> result = new ArrayList<>();
		int seatNo = candidates.findByRoomIdOrderBySeatNoAsc(roomId).stream()
				.map(ExamCandidate::getSeatNo)
				.filter(Objects::nonNull)
				.max(Comparator.naturalOrder())
				.orElse(0);
		for (String studentId : new HashSet<>(command.studentIds())) {
			if (!students.existsById(studentId)) {
				throw new IllegalArgumentException("学生不存在：" + studentId);
			}
			if (candidates.existsByRoomIdAndStudentId(roomId, studentId)) {
				continue;
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
		return result;
	}

	@Transactional(readOnly = true)
	public List<TeacherAcademicProfile> availableTeachers(String roomId) {
		ExamRoom room = room(roomId);
		ExamSession session = session(room.getSessionId());
		return teachers.findAllByOrderByTeacherNo().stream()
				.filter(item -> Boolean.TRUE.equals(item.getEnabled()))
				.filter(item -> teacherConflicts(item.getId(), session, null).isEmpty())
				.toList();
	}

	@Transactional
	public ExamInvigilation assign(String roomId, ExamCommands.Assignment command) {
		ExamRoom room = room(roomId);
		ExamSession session = session(room.getSessionId());
		draft(session.getPlanId());
		if (!Set.of("CHIEF", "ASSISTANT").contains(command.dutyRole())) {
			throw new IllegalArgumentException("监考岗位无效");
		}
		assertTeacherAvailable(command.teacherId(), session, null);
		if (assignments.findByRoomIdAndStatus(roomId, "ASSIGNED").size()
				>= room.getRequiredInvigilators()) {
			throw new IllegalStateException("考场监考人数已满");
		}
		ExamInvigilation value = new ExamInvigilation();
		value.setRoomId(roomId);
		value.setTeacherId(command.teacherId());
		value.setDutyRole(command.dutyRole());
		return assignments.save(value);
	}

	/** 发布时重新校验所有资源；冲突课程须先在调停课模块中处理。 */
	@Transactional
	public ExamPlan publish(String planId) {
		ExamPlan plan = draft(planId);
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
				List<String> conflicts = roomConflicts(room, session);
				if (!conflicts.isEmpty()) {
					throw new IllegalStateException(String.join("；", conflicts));
				}
				List<ExamInvigilation> staff = assignments.findByRoomIdAndStatus(
						room.getId(), "ASSIGNED");
				if (staff.size() < room.getRequiredInvigilators()
						|| staff.stream().noneMatch(item -> "CHIEF".equals(item.getDutyRole()))) {
					throw new IllegalStateException("考场监考人数不足或缺少主监考");
				}
				for (ExamInvigilation item : staff) {
					assertTeacherAvailable(item.getTeacherId(), session, item.getId());
				}
				room.setStatus("PUBLISHED");
			}
			session.setStatus("PUBLISHED");
		}
		plan.setStatus("PUBLISHED");
		return plans.save(plan);
	}

	@Transactional
	public ExamInvigilationChange requestChange(
			String assignmentId,
			ExamCommands.Change command,
			String username,
			boolean emergency) {
		ExamInvigilation assignment = assignment(assignmentId);
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
		return changes.save(value);
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
			String teacherId = command.replacementTeacherId() == null
					? change.getProposedTeacherId() : command.replacementTeacherId();
			ExamRoom room = room(old.getRoomId());
			assertTeacherAvailable(teacherId, session(room.getSessionId()), old.getId());
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
		return changes.save(change);
	}

	private List<String> roomConflicts(ExamRoom room, ExamSession session) {
		List<String> conflicts = new ArrayList<>();
		for (ExamSession other : overlapping(session)) {
			if (!other.getId().equals(session.getId())
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
			String excludeAssignmentId) {
		if (teacherId == null || teachers.findById(teacherId)
				.filter(item -> Boolean.TRUE.equals(item.getEnabled())).isEmpty()) {
			throw new IllegalArgumentException("监考教师不存在或已停用");
		}
		List<String> conflicts = teacherConflicts(teacherId, session, excludeAssignmentId);
		if (!conflicts.isEmpty()) {
			throw new IllegalStateException(String.join("；", conflicts));
		}
	}

	private List<String> teacherConflicts(
			String teacherId,
			ExamSession session,
			String excludeAssignmentId) {
		List<String> conflicts = new ArrayList<>();
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
		for (ExamInvigilation assignment : assignments.findByTeacherIdAndStatus(
				teacherId, "ASSIGNED")) {
			if (assignment.getId().equals(excludeAssignmentId)) {
				continue;
			}
			ExamSession other = session(room(assignment.getRoomId()).getSessionId());
			if (sameTime(session, other)) {
				conflicts.add("教师已有监考任务");
			}
		}
		return conflicts;
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
