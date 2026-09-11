package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleCandidatePlanRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherTimeConstraintRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.ClassroomUnavailableSlot;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleCandidateMetrics;
import com.chronos.education.scheduling.model.ScheduleCandidatePlan;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.model.ScheduleDiffItem;
import com.chronos.education.scheduling.model.ScheduleDiffView;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.TeacherTimeConstraint;
import com.chronos.education.scheduling.model.TeachingClassMember;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 生成、比较和应用自动排课候选方案。
 *
 * <p>候选生成只写候选表；只有显式应用且基线哈希未变化时，才替换当前草稿课表。</p>
 */
@Service
public class AutoSchedulingService {
	private static final int DEFAULT_WEEKDAYS = 5;
	private static final int DEFAULT_PERIODS = 8;
	private static final int MAX_CANDIDATES = 5;
	private final ScheduleCandidatePlanRepository candidates;
	private final ScheduleEntryRepository entries;
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final ClassroomUnavailableSlotRepository unavailableSlots;
	private final TeacherTimeConstraintRepository constraints;
	private final TeachingClassMemberRepository members;
	private final AcademicTermRepository terms;
	private final AcademicCalendarService academicCalendar;
	private final IAuditLogService audit;
	private final EntityManager entityManager;
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

	public AutoSchedulingService(
			ScheduleCandidatePlanRepository candidates,
			ScheduleEntryRepository entries,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			ClassroomUnavailableSlotRepository unavailableSlots,
			TeacherTimeConstraintRepository constraints,
			TeachingClassMemberRepository members,
			AcademicTermRepository terms,
			AcademicCalendarService academicCalendar,
			IAuditLogService audit,
			EntityManager entityManager) {
		this.candidates = candidates;
		this.entries = entries;
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.unavailableSlots = unavailableSlots;
		this.constraints = constraints;
		this.members = members;
		this.terms = terms;
		this.academicCalendar = academicCalendar;
		this.audit = audit;
		this.entityManager = entityManager;
	}

	@Transactional
	public List<ScheduleCandidateView> generate(
			AutoScheduleCommand command,
			String actor) {
		GenerationRequest request = validate(command);
		terms.findByTermCode(request.semesterCode())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		List<ScheduleEntry> baseline = current(request.semesterCode());
		String baselineHash = hash(baseline);
		List<CourseOffering> semesterOfferings = offerings
				.findBySemesterCodeOrderByOfferingCode(request.semesterCode()).stream()
				.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.toList();
		Set<String> targetIds = targetOfferingIds(request, semesterOfferings);
		List<ScheduleCandidateView> result = new ArrayList<>();
		for (int index = 0; index < request.candidateCount(); index++) {
			GeneratedPlan generated = buildCandidate(
					request,
					baseline,
					semesterOfferings,
					targetIds,
					index);
			ScheduleCandidatePlan candidate = new ScheduleCandidatePlan();
			candidate.setSemesterCode(request.semesterCode());
			candidate.setPlanName(request.planName() + "-方案" + (index + 1));
			candidate.setGenerationMode(request.mode());
			candidate.setScopeJson(write(Map.of(
					"offeringIds", targetIds,
					"weekdays", request.weekdays(),
					"periodsPerDay", request.periodsPerDay(),
					"startWeek", request.startWeek(),
					"endWeek", request.endWeek())));
			candidate.setBaselineHash(baselineHash);
			candidate.setSnapshotJson(write(generated.entries()));
			candidate.setMetricsJson(write(generated.metrics()));
			candidate.setEntryCount(generated.entries().size());
			candidate.setUnscheduledLessons(generated.metrics().unscheduledLessons());
			candidate.setTotalScore(generated.metrics().totalScore());
			candidate.setGeneratedBy(actor);
			candidate.setGeneratedAt(LocalDateTime.now());
			result.add(view(candidates.save(candidate)));
		}
		audit.log(
				actor,
				"EDUCATION_AUTO_SCHEDULE_GENERATE",
				"semester=" + request.semesterCode()
						+ ", mode=" + request.mode()
						+ ", candidates=" + result.size()
						+ ", offerings=" + targetIds.size());
		return result.stream()
				.sorted(Comparator.comparing(ScheduleCandidateView::totalScore).reversed())
				.toList();
	}

	@Transactional(readOnly = true)
	public List<ScheduleCandidateView> list(String semesterCode) {
		return candidates.findBySemesterCodeOrderByGeneratedAtDesc(required(semesterCode, "学期编码"))
				.stream()
				.map(this::view)
				.toList();
	}

	@Transactional(readOnly = true)
	public ScheduleDiffView preview(String candidateId) {
		ScheduleCandidatePlan candidate = candidate(candidateId);
		return diff(
				current(candidate.getSemesterCode()),
				readEntries(candidate.getSnapshotJson()),
				candidate.getSemesterCode());
	}

	@Transactional(readOnly = true)
	public ScheduleDiffView publicationPreview(
			String semesterCode,
			List<ScheduleEntry> published) {
		String semester = required(semesterCode, "学期编码");
		return diff(published, current(semester), semester);
	}

	@Transactional
	public ScheduleCandidateView apply(String candidateId, String actor) {
		ScheduleCandidatePlan candidate = candidate(candidateId);
		if (!"CANDIDATE".equals(candidate.getStatus())) {
			throw new IllegalStateException("只有候选状态的方案可以应用");
		}
		if (candidate.getUnscheduledLessons() > 0) {
			throw new IllegalStateException("候选方案仍有未排课课时，不能应用");
		}
		terms.findForUpdateByTermCode(candidate.getSemesterCode())
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		List<ScheduleEntry> baseline = current(candidate.getSemesterCode());
		if (!candidate.getBaselineHash().equals(hash(baseline))) {
			throw new IllegalStateException("当前课表在候选方案生成后已变化，请重新生成方案");
		}
		List<ScheduleEntry> snapshot = readEntries(candidate.getSnapshotJson());
		entries.deleteAllForRollback(candidate.getSemesterCode());
		entityManager.flush();
		entityManager.clear();
		for (ScheduleEntry entry : snapshot) {
			insertEntry(entry, actor);
		}
		candidate = candidates.findById(candidateId)
				.orElseThrow(() -> new IllegalStateException("候选方案已被并发删除"));
		candidate.setStatus("APPLIED");
		candidate.setAppliedBy(actor);
		candidate.setAppliedAt(LocalDateTime.now());
		candidate = candidates.save(candidate);
		audit.log(
				actor,
				"EDUCATION_AUTO_SCHEDULE_APPLY",
				"candidate=" + candidateId + ", semester=" + candidate.getSemesterCode());
		return view(candidate);
	}

	@Transactional
	public ScheduleCandidateView discard(String candidateId, String actor) {
		ScheduleCandidatePlan candidate = candidate(candidateId);
		if ("APPLIED".equals(candidate.getStatus())) {
			throw new IllegalStateException("已应用方案不能废弃");
		}
		candidate.setStatus("DISCARDED");
		candidate = candidates.save(candidate);
		audit.log(actor, "EDUCATION_AUTO_SCHEDULE_DISCARD", "candidate=" + candidateId);
		return view(candidate);
	}

	private GeneratedPlan buildCandidate(
			GenerationRequest request,
			List<ScheduleEntry> baseline,
			List<CourseOffering> semesterOfferings,
			Set<String> targetIds,
			int variation) {
		Map<String, CourseOffering> offeringById = semesterOfferings.stream()
				.collect(Collectors.toMap(CourseOffering::getId, value -> value));
		List<ScheduleEntry> result = baseline.stream()
				.filter(entry -> "CANCELLED".equals(entry.getStatus())
						|| !targetIds.contains(entry.getOfferingId())
						|| Boolean.TRUE.equals(entry.getLocked()))
				.map(this::copy)
				.collect(Collectors.toCollection(ArrayList::new));
		Map<String, Deque<String>> reusableIds = baseline.stream()
				.filter(entry -> targetIds.contains(entry.getOfferingId()))
				.filter(entry -> !Boolean.TRUE.equals(entry.getLocked()))
				// 已取消记录作为历史保留，不能把它的主键复用于新排课项。
				.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
				.collect(Collectors.groupingBy(
						ScheduleEntry::getOfferingId,
						Collectors.mapping(
								ScheduleEntry::getId,
								Collectors.toCollection(ArrayDeque::new))));
		Map<String, Set<String>> studentIds = studentIdsByOffering(
				semesterOfferings.stream()
						.map(CourseOffering::getId)
						.toList());
		Map<String, List<TeacherTimeConstraint>> teacherConstraints = constraints
				.findBySemesterCodeOrderByTeacherIdAscDayOfWeekAscPeriodNoAsc(request.semesterCode())
				.stream()
				.collect(Collectors.groupingBy(TeacherTimeConstraint::getTeacherId));
		List<Classroom> availableRooms = classrooms.findByEnabledTrueOrderByRoomCode();
		List<ClassroomUnavailableSlot> roomUnavailableSlots = unavailableSlots
				.findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc(request.semesterCode())
				.stream()
				.filter(item -> "ACTIVE".equals(item.getStatus()))
				.toList();
		List<CourseOffering> targets = semesterOfferings.stream()
				.filter(offering -> targetIds.contains(offering.getId()))
				.sorted(Comparator.comparing(CourseOffering::getStudentCount).reversed()
						.thenComparing(
								Comparator.comparing(CourseOffering::getWeeklyLessons).reversed())
						.thenComparing(CourseOffering::getOfferingCode))
				.toList();
		List<Slot> slots = slots(request.weekdays(), request.periodsPerDay(), variation);
		// 热点循环只查询内存索引，避免每个“时段 × 教室”组合重复扫描完整课表。
		SchedulingIndex schedulingIndex = new SchedulingIndex(
				result,
				offeringById,
				studentIds);
		int scheduled = 0;
		int unscheduled = 0;
		int preferredHits = 0;
		int sameCourseDayPenalty = 0;
		Map<String, Set<Integer>> periodsByCampus = new HashMap<>();
		for (CourseOffering offering : targets) {
			int lockedLessons = result.stream()
					.filter(entry -> offering.getId().equals(entry.getOfferingId()))
					.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
					.mapToInt(entry -> entry.getDurationPeriods() == null ? 1 : entry.getDurationPeriods())
					.sum();
			int requiredLessons = Math.max(0, offering.getWeeklyLessons() - lockedLessons);
			int preferredDuration = Math.max(1, offering.getPreferredDurationPeriods() == null
					? 1
					: offering.getPreferredDurationPeriods());
			Set<Integer> allowedPeriods = periodsByCampus.computeIfAbsent(
					offering.getCampusId() == null ? "" : offering.getCampusId(),
					campus -> academicCalendar.schedulablePeriodNumbers(
							request.semesterCode(),
							offering.getCampusId(),
							request.periodsPerDay()));
			for (int lesson = 0; lesson < requiredLessons;) {
				int duration = Math.min(preferredDuration, requiredLessons - lesson);
				Placement placement = bestPlacement(
						offering,
						slots,
						availableRooms,
						schedulingIndex,
						teacherConstraints.getOrDefault(offering.getTeacherId(), List.of()),
						allowedPeriods,
						duration,
						roomUnavailableSlots);
				if (placement == null) {
					unscheduled += duration;
					lesson += duration;
					continue;
				}
				ScheduleEntry generated = new ScheduleEntry();
				Deque<String> ids = reusableIds.get(offering.getId());
				if (ids != null && !ids.isEmpty()) {
					generated.setId(ids.removeFirst());
				}
				generated.setSemesterCode(request.semesterCode());
				generated.setOfferingId(offering.getId());
				generated.setClassroomId(placement.classroom().getId());
				generated.setDayOfWeek(placement.slot().day());
				generated.setPeriodNo(placement.slot().period());
				generated.setDurationPeriods(duration);
				generated.setWeekPattern(offering.getWeekPattern() == null ? "ALL" : offering.getWeekPattern());
				generated.setStartWeek(request.startWeek());
				generated.setEndWeek(request.endWeek());
				generated.setStatus("SCHEDULED");
				generated.setLocked(false);
				result.add(generated);
				schedulingIndex.add(generated);
				scheduled += duration;
				lesson += duration;
				if (placement.preferred()) {
					preferredHits++;
				}
				sameCourseDayPenalty += placement.sameCourseDayCount();
			}
		}
		result.sort(entryComparator());
		int score = scheduled * 100
				+ preferredHits * 10
				- sameCourseDayPenalty * 5
				- unscheduled * 1_000;
		return new GeneratedPlan(
				result,
				new ScheduleCandidateMetrics(
						scheduled,
						unscheduled,
						preferredHits,
						sameCourseDayPenalty,
						score));
	}

	private Placement bestPlacement(
			CourseOffering offering,
			List<Slot> slots,
			List<Classroom> availableRooms,
			SchedulingIndex schedulingIndex,
			List<TeacherTimeConstraint> teacherConstraints,
			Set<Integer> allowedPeriods,
			int duration,
			List<ClassroomUnavailableSlot> roomUnavailableSlots) {
		Placement best = null;
		for (Slot slot : slots) {
			if (!consecutivePeriodsAllowed(allowedPeriods, slot.period(), duration)
					|| forbidden(teacherConstraints, slot, duration)) {
				continue;
			}
			for (Classroom room : availableRooms) {
				if (!roomSuitable(offering, room)
						|| roomUnavailable(roomUnavailableSlots, room, slot, duration)
						|| schedulingIndex.conflicts(offering, room, slot, duration)) {
					continue;
				}
				int sameDay = schedulingIndex.sameCourseDayCount(offering, slot);
				int teacherDayLoad = schedulingIndex.teacherDayLoad(offering, slot);
				boolean preferred = preferred(teacherConstraints, slot);
				int penalty = sameDay * 20 + teacherDayLoad * 2 - (preferred ? 10 : 0);
				Placement candidate = new Placement(slot, room, penalty, preferred, sameDay);
				if (best == null || candidate.penalty() < best.penalty()) {
					best = candidate;
				}
			}
		}
		return best;
	}

	private boolean roomSuitable(CourseOffering offering, Classroom room) {
		return room.getCapacity() >= offering.getStudentCount()
				&& containsAllCodes(room.getEquipmentCodes(), offering.getRequiredEquipmentCodes())
				&& (offering.getRequiredRoomType() == null
						|| offering.getRequiredRoomType().isBlank()
						|| offering.getRequiredRoomType().equals(room.getRoomType()))
				&& (offering.getCampusId() == null
						|| offering.getCampusId().isBlank()
						|| offering.getCampusId().equals(room.getCampusId()));
	}

	private boolean roomUnavailable(
			List<ClassroomUnavailableSlot> values,
			Classroom room,
			Slot slot,
			int duration) {
		return values.stream().anyMatch(value -> room.getId().equals(value.getClassroomId())
				&& value.getDayOfWeek() == slot.day()
				&& slot.period() <= value.getEndPeriod()
				&& slot.period() + duration - 1 >= value.getStartPeriod());
	}

	private boolean containsAllCodes(String actual, String requiredCodes) {
		if (requiredCodes == null || requiredCodes.isBlank()) {
			return true;
		}
		Set<String> actualValues = java.util.Arrays.stream(
				actual == null ? new String[0] : actual.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.collect(Collectors.toSet());
		return java.util.Arrays.stream(requiredCodes.split(","))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.allMatch(actualValues::contains);
	}

	private boolean forbidden(List<TeacherTimeConstraint> values, Slot slot, int duration) {
		return values.stream().anyMatch(value -> "FORBIDDEN".equals(value.getConstraintType())
				&& value.getDayOfWeek() == slot.day()
				&& value.getPeriodNo() >= slot.period()
				&& value.getPeriodNo() < slot.period() + duration);
	}

	private boolean consecutivePeriodsAllowed(Set<Integer> values, int start, int duration) {
		for (int period = start; period < start + duration; period++) {
			if (!values.contains(period)) {
				return false;
			}
		}
		return true;
	}

	private boolean preferred(List<TeacherTimeConstraint> values, Slot slot) {
		return values.stream().anyMatch(value -> "PREFERRED".equals(value.getConstraintType())
				&& value.getDayOfWeek() == slot.day()
				&& value.getPeriodNo() == slot.period());
	}

	private Map<String, Set<String>> studentIdsByOffering(List<String> offeringIds) {
		if (offeringIds.isEmpty()) {
			return Map.of();
		}
		return members.findByOfferingIdInAndEnrollmentStatus(
				offeringIds,
				"ENROLLED").stream()
				.collect(Collectors.groupingBy(
						TeachingClassMember::getOfferingId,
						Collectors.mapping(
								TeachingClassMember::getStudentId,
								Collectors.toSet())));
	}

	private List<Slot> slots(int weekdays, int periodsPerDay, int variation) {
		List<Slot> slots = new ArrayList<>();
		for (int day = 1; day <= weekdays; day++) {
			for (int period = 1; period <= periodsPerDay; period++) {
				slots.add(new Slot(day, period));
			}
		}
		if (!slots.isEmpty()) {
			java.util.Collections.rotate(slots, -(variation % slots.size()));
		}
		return slots;
	}

	private ScheduleDiffView diff(
			List<ScheduleEntry> before,
			List<ScheduleEntry> after,
			String semesterCode) {
		Map<String, CourseOffering> offeringById = offerings
				.findBySemesterCodeOrderByOfferingCode(semesterCode)
				.stream()
				.collect(Collectors.toMap(CourseOffering::getId, value -> value));
		Map<String, Classroom> roomById = classrooms.findAll().stream()
				.collect(Collectors.toMap(Classroom::getId, value -> value));
		Set<String> offeringIds = new HashSet<>();
		before.forEach(entry -> offeringIds.add(entry.getOfferingId()));
		after.forEach(entry -> offeringIds.add(entry.getOfferingId()));
		List<ScheduleDiffItem> items = new ArrayList<>();
		int added = 0;
		int removed = 0;
		int moved = 0;
		int unchanged = 0;
		for (String offeringId : offeringIds) {
			List<ScheduleEntry> oldEntries = before.stream()
					.filter(entry -> offeringId.equals(entry.getOfferingId()))
					.sorted(entryComparator())
					.collect(Collectors.toCollection(ArrayList::new));
			List<ScheduleEntry> newEntries = after.stream()
					.filter(entry -> offeringId.equals(entry.getOfferingId()))
					.sorted(entryComparator())
					.collect(Collectors.toCollection(ArrayList::new));
			for (int oldIndex = oldEntries.size() - 1; oldIndex >= 0; oldIndex--) {
				ScheduleEntry oldEntry = oldEntries.get(oldIndex);
				int exact = indexOfSlot(newEntries, oldEntry);
				if (exact >= 0) {
					unchanged++;
					oldEntries.remove(oldIndex);
					newEntries.remove(exact);
				}
			}
			CourseOffering offering = offeringById.get(offeringId);
			while (!oldEntries.isEmpty() && !newEntries.isEmpty()) {
				ScheduleEntry oldEntry = oldEntries.remove(0);
				ScheduleEntry newEntry = newEntries.remove(0);
				items.add(diffItem("MOVED", offering, oldEntry, newEntry, roomById));
				moved++;
			}
			for (ScheduleEntry oldEntry : oldEntries) {
				items.add(diffItem("REMOVED", offering, oldEntry, null, roomById));
				removed++;
			}
			for (ScheduleEntry newEntry : newEntries) {
				items.add(diffItem("ADDED", offering, null, newEntry, roomById));
				added++;
			}
		}
		return new ScheduleDiffView(added, removed, moved, unchanged, items);
	}

	private int indexOfSlot(List<ScheduleEntry> values, ScheduleEntry expected) {
		for (int index = 0; index < values.size(); index++) {
			if (slotKey(values.get(index)).equals(slotKey(expected))) {
				return index;
			}
		}
		return -1;
	}

	private ScheduleDiffItem diffItem(
			String type,
			CourseOffering offering,
			ScheduleEntry before,
			ScheduleEntry after,
			Map<String, Classroom> roomById) {
		return new ScheduleDiffItem(
				type,
				offering == null ? "" : offering.getId(),
				offering == null ? "未知课程" : offering.getCourseName(),
				offering == null ? "" : offering.getTeachingClassName(),
				offering == null ? "" : offering.getTeacherName(),
				slotDescription(before, roomById),
				slotDescription(after, roomById));
	}

	private String slotDescription(
			ScheduleEntry entry,
			Map<String, Classroom> roomById) {
		if (entry == null) {
			return "";
		}
		Classroom room = roomById.get(entry.getClassroomId());
		return "周" + entry.getDayOfWeek()
				+ " 第" + entry.getPeriodNo() + "节"
				+ " / " + (room == null ? "未知教室" : room.getRoomName());
	}

	private String slotKey(ScheduleEntry entry) {
		return entry.getDayOfWeek() + "|"
				+ entry.getPeriodNo() + "|"
				+ entry.getDurationPeriods() + "|"
				+ entry.getClassroomId() + "|"
				+ entry.getWeekPattern() + "|"
				+ entry.getStartWeek() + "|"
				+ entry.getEndWeek();
	}

	private void insertEntry(ScheduleEntry entry, String actor) {
		String id = entry.getId() == null || entry.getId().isBlank()
				? java.util.UUID.randomUUID().toString()
				: entry.getId();
		LocalDateTime now = LocalDateTime.now();
		entityManager.createNativeQuery("""
				insert into edu_schedule_entry (
				    id, create_by, create_time, last_update_by, last_update_time,
				    semester_code, offering_id, classroom_id, day_of_week, period_no,
				    duration_periods, week_pattern, start_week, end_week, status,
				    substitute_teacher_id, source_adjustment_instance_id, locked
				) values (
				    :id, :createBy, :createTime, :lastUpdateBy, :lastUpdateTime,
				    :semesterCode, :offeringId, :classroomId, :dayOfWeek, :periodNo,
				    :durationPeriods, :weekPattern, :startWeek, :endWeek, :status,
				    :substituteTeacherId, :sourceAdjustmentInstanceId, :locked
				)
				""")
				.setParameter("id", id)
				.setParameter("createBy", entry.getCreateBy() == null ? actor : entry.getCreateBy())
				.setParameter("createTime", entry.getCreateTime() == null ? now : entry.getCreateTime())
				.setParameter("lastUpdateBy", actor)
				.setParameter("lastUpdateTime", now)
				.setParameter("semesterCode", entry.getSemesterCode())
				.setParameter("offeringId", entry.getOfferingId())
				.setParameter("classroomId", entry.getClassroomId())
				.setParameter("dayOfWeek", entry.getDayOfWeek())
				.setParameter("periodNo", entry.getPeriodNo())
				.setParameter("durationPeriods", entry.getDurationPeriods())
				.setParameter("weekPattern", entry.getWeekPattern())
				.setParameter("startWeek", entry.getStartWeek())
				.setParameter("endWeek", entry.getEndWeek())
				.setParameter("status", entry.getStatus())
				.setParameter("substituteTeacherId", entry.getSubstituteTeacherId())
				.setParameter("sourceAdjustmentInstanceId", entry.getSourceAdjustmentInstanceId())
				.setParameter("locked", Boolean.TRUE.equals(entry.getLocked()))
				.executeUpdate();
	}

	private GenerationRequest validate(AutoScheduleCommand command) {
		if (command == null) {
			throw new IllegalArgumentException("自动排课参数不能为空");
		}
		String mode = command.mode() == null ? "FULL" : command.mode().trim().toUpperCase();
		if (!Set.of("FULL", "LOCAL").contains(mode)) {
			throw new IllegalArgumentException("排课模式必须为 FULL 或 LOCAL");
		}
		Set<String> selected = command.selectedOfferingIds() == null
				? Set.of()
				: command.selectedOfferingIds().stream()
						.filter(value -> value != null && !value.isBlank())
						.collect(Collectors.toSet());
		if ("LOCAL".equals(mode) && selected.isEmpty()) {
			throw new IllegalArgumentException("局部重排至少选择一个教学任务");
		}
		int startWeek = range(command.startWeek(), 1, 52, 1, "开始周");
		int endWeek = range(command.endWeek(), 1, 52, 20, "结束周");
		if (endWeek < startWeek) {
			throw new IllegalArgumentException("结束周不能早于开始周");
		}
		return new GenerationRequest(
				required(command.semesterCode(), "学期编码"),
				required(command.planName(), "方案名称"),
				mode,
				selected,
				range(command.candidateCount(), 1, MAX_CANDIDATES, 3, "候选方案数"),
				range(command.weekdays(), 1, 7, DEFAULT_WEEKDAYS, "上课天数"),
				range(command.periodsPerDay(), 1, 20, DEFAULT_PERIODS, "每日节数"),
				startWeek,
				endWeek);
	}

	private Set<String> targetOfferingIds(
			GenerationRequest request,
			List<CourseOffering> semesterOfferings) {
		Set<String> available = semesterOfferings.stream()
				.map(CourseOffering::getId)
				.collect(Collectors.toSet());
		if ("FULL".equals(request.mode())) {
			return available;
		}
		if (!available.containsAll(request.selectedOfferingIds())) {
			throw new IllegalArgumentException("局部重排包含不属于当前学期的教学任务");
		}
		return request.selectedOfferingIds();
	}

	private int range(
			Integer value,
			int minimum,
			int maximum,
			int defaultValue,
			String label) {
		int resolved = value == null ? defaultValue : value;
		if (resolved < minimum || resolved > maximum) {
			throw new IllegalArgumentException(label + "必须在 " + minimum + " 到 " + maximum + " 之间");
		}
		return resolved;
	}

	private List<ScheduleEntry> current(String semesterCode) {
		return entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(semesterCode).stream()
				.toList();
	}

	private ScheduleCandidatePlan candidate(String id) {
		return candidates.findById(required(id, "候选方案"))
				.orElseThrow(() -> new IllegalArgumentException("候选方案不存在"));
	}

	private ScheduleCandidateView view(ScheduleCandidatePlan candidate) {
		return new ScheduleCandidateView(
				candidate.getId(),
				candidate.getSemesterCode(),
				candidate.getPlanName(),
				candidate.getGenerationMode(),
				candidate.getEntryCount(),
				candidate.getUnscheduledLessons(),
				candidate.getTotalScore(),
				candidate.getStatus(),
				candidate.getGeneratedBy(),
				candidate.getGeneratedAt(),
				candidate.getAppliedBy(),
				candidate.getAppliedAt(),
				read(candidate.getMetricsJson(), ScheduleCandidateMetrics.class));
	}

	private ScheduleEntry copy(ScheduleEntry source) {
		return json.convertValue(source, ScheduleEntry.class);
	}

	private List<ScheduleEntry> readEntries(String value) {
		try {
			return json.readValue(value, new TypeReference<>() { });
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("候选方案快照损坏", exception);
		}
	}

	private <T> T read(String value, Class<T> type) {
		try {
			return json.readValue(value, type);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("候选方案指标损坏", exception);
		}
	}

	private String write(Object value) {
		try {
			return json.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("候选方案序列化失败", exception);
		}
	}

	private String hash(List<ScheduleEntry> values) {
		String canonical = values.stream()
				.sorted(entryComparator())
				.map(entry -> (entry.getId() == null ? "" : entry.getId())
						+ "|" + entry.getOfferingId()
						+ "|" + slotKey(entry)
						+ "|" + entry.getStatus()
						+ "|" + entry.getLocked())
				.collect(Collectors.joining("\n"));
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(canonical.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("运行环境不支持 SHA-256", exception);
		}
	}

	private Comparator<ScheduleEntry> entryComparator() {
		return Comparator.comparing(ScheduleEntry::getOfferingId)
				.thenComparing(ScheduleEntry::getDayOfWeek)
				.thenComparing(ScheduleEntry::getPeriodNo)
				.thenComparing(entry -> entry.getId() == null ? "" : entry.getId());
	}

	private String required(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(label + "不能为空");
		}
		return value.trim();
	}

	private record GenerationRequest(
			String semesterCode,
			String planName,
			String mode,
			Set<String> selectedOfferingIds,
			int candidateCount,
			int weekdays,
			int periodsPerDay,
			int startWeek,
			int endWeek) {
	}

	private record Slot(int day, int period) {
	}

	private record Placement(
			Slot slot,
			Classroom classroom,
			int penalty,
			boolean preferred,
			int sameCourseDayCount) {
	}

	private record GeneratedPlan(
			List<ScheduleEntry> entries,
			ScheduleCandidateMetrics metrics) {
	}

	/** 当前候选方案的冲突和评分索引；添加新课时后增量更新。 */
	private static final class SchedulingIndex {
		private final Map<String, CourseOffering> offeringById;
		private final Map<String, Set<String>> studentIds;
		private final Set<String> roomSlots = new HashSet<>();
		private final Set<String> offeringSlots = new HashSet<>();
		private final Set<String> teacherSlots = new HashSet<>();
		private final Set<String> studentSlots = new HashSet<>();
		private final Map<String, Integer> offeringDayCounts = new HashMap<>();
		private final Map<String, Integer> teacherDayCounts = new HashMap<>();

		private SchedulingIndex(
				List<ScheduleEntry> scheduled,
				Map<String, CourseOffering> offeringById,
				Map<String, Set<String>> studentIds) {
			this.offeringById = offeringById;
			this.studentIds = studentIds;
			scheduled.stream()
					.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
					.forEach(this::add);
		}

		private void add(ScheduleEntry entry) {
			CourseOffering offering = offeringById.get(entry.getOfferingId());
			if (offering == null) {
				return;
			}
			int duration = entry.getDurationPeriods() == null
					? 1
					: Math.max(1, entry.getDurationPeriods());
			for (int offset = 0; offset < duration; offset++) {
				String slot = slot(entry.getDayOfWeek(), entry.getPeriodNo() + offset);
				roomSlots.add(slot + "|" + entry.getClassroomId());
				offeringSlots.add(slot + "|" + entry.getOfferingId());
				teacherSlots.add(slot + "|" + offering.getTeacherId());
				studentIds.getOrDefault(entry.getOfferingId(), Set.of())
						.forEach(studentId -> studentSlots.add(slot + "|" + studentId));
			}
			offeringDayCounts.merge(
					entry.getOfferingId() + "|" + entry.getDayOfWeek(),
					1,
					Integer::sum);
			teacherDayCounts.merge(
					offering.getTeacherId() + "|" + entry.getDayOfWeek(),
					1,
					Integer::sum);
		}

		private boolean conflicts(CourseOffering offering, Classroom room, Slot slot, int duration) {
			for (int offset = 0; offset < duration; offset++) {
				String slotKey = slot(slot.day(), slot.period() + offset);
				if (roomSlots.contains(slotKey + "|" + room.getId())
						|| offeringSlots.contains(slotKey + "|" + offering.getId())
						|| teacherSlots.contains(slotKey + "|" + offering.getTeacherId())
						|| studentIds.getOrDefault(offering.getId(), Set.of()).stream()
								.anyMatch(studentId -> studentSlots.contains(slotKey + "|" + studentId))) {
					return true;
				}
			}
			return false;
		}

		private int sameCourseDayCount(CourseOffering offering, Slot slot) {
			return offeringDayCounts.getOrDefault(
					offering.getId() + "|" + slot.day(),
					0);
		}

		private int teacherDayLoad(CourseOffering offering, Slot slot) {
			return teacherDayCounts.getOrDefault(
					offering.getTeacherId() + "|" + slot.day(),
					0);
		}

		private String slot(int day, int period) {
			return day + "|" + period;
		}
	}
}
