package com.chronos.education.scheduling.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.ClassroomRepository;
import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.ClassroomUnavailableSlotRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.dao.TeachingClassMemberRepository;
import com.chronos.education.scheduling.model.Classroom;
import com.chronos.education.scheduling.model.AcademicCalendarDay;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.ClassroomUnavailableSlot;
import com.chronos.education.scheduling.model.CourseOffering;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePolicy;
import com.chronos.education.scheduling.model.TeacherAcademicProfile;
import com.chronos.education.scheduling.model.TeachingClassMember;

/** 对当前排课草稿执行可解释的负载、利用率和分布质量分析。 */
@Service
public class ScheduleQualityAnalysisService {
	private final ScheduleEntryRepository entries;
	private final CourseOfferingRepository offerings;
	private final ClassroomRepository classrooms;
	private final TeacherAcademicProfileRepository teachers;
	private final SchedulePolicyService policies;
	private final TeachingClassMemberRepository members;
	private final AcademicTermRepository terms;
	private final AcademicCalendarDayRepository calendarDays;
	private final BellScheduleRepository bellSchedules;
	private final BellPeriodRepository bellPeriods;
	private final ClassroomUnavailableSlotRepository unavailableSlots;

	public ScheduleQualityAnalysisService(
			ScheduleEntryRepository entries,
			CourseOfferingRepository offerings,
			ClassroomRepository classrooms,
			TeacherAcademicProfileRepository teachers,
			SchedulePolicyService policies,
			TeachingClassMemberRepository members,
			AcademicTermRepository terms,
			AcademicCalendarDayRepository calendarDays,
			BellScheduleRepository bellSchedules,
			BellPeriodRepository bellPeriods,
			ClassroomUnavailableSlotRepository unavailableSlots) {
		this.entries = entries;
		this.offerings = offerings;
		this.classrooms = classrooms;
		this.teachers = teachers;
		this.policies = policies;
		this.members = members;
		this.terms = terms;
		this.calendarDays = calendarDays;
		this.bellSchedules = bellSchedules;
		this.bellPeriods = bellPeriods;
		this.unavailableSlots = unavailableSlots;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> analyze(String semesterCode) {
		SchedulePolicy policy = policies.resolve(semesterCode);
		List<ScheduleEntry> schedule = entries
				.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(semesterCode)
				.stream()
				.filter(entry -> !"CANCELLED".equals(entry.getStatus()))
				.toList();
		Map<String, CourseOffering> offeringById = offerings
				.findBySemesterCodeOrderByOfferingCode(semesterCode)
				.stream()
				.collect(Collectors.toMap(CourseOffering::getId, item -> item));
		Map<String, TeacherAcademicProfile> teacherById = teachers.findAll().stream()
				.collect(Collectors.toMap(TeacherAcademicProfile::getId, item -> item));
		Map<String, Classroom> classroomById = classrooms.findAll().stream()
				.collect(Collectors.toMap(Classroom::getId, item -> item));

		List<Map<String, Object>> teacherLoads = teacherLoads(
				schedule,
				offeringById,
				teacherById,
				policy);
		List<Map<String, Object>> roomUtilization = roomUtilization(
				semesterCode,
				schedule,
				classroomById);
		List<Map<String, Object>> distributionRisks = distributionRisks(
				schedule,
				offeringById,
				policy.getCourseConcentrationThreshold());
		List<Map<String, Object>> risks = new ArrayList<>();
		risks.addAll(hardConflictRisks(schedule, offeringById));
		risks.addAll(incompleteOfferingRisks(schedule, offeringById));
		teacherLoads.stream()
				.filter(item -> Boolean.TRUE.equals(item.get("overloaded")))
				.forEach(item -> risks.add(risk(
						"TEACHER_OVERLOAD",
						"HIGH",
						item.get("teacherName") + " 的课时或连续授课超过上限")));
		distributionRisks.forEach(item -> risks.add(risk(
				"COURSE_DISTRIBUTION",
				"MEDIUM",
				item.get("courseName") + " 在同一天安排过于集中")));

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("summary", Map.of(
				"entryCount", schedule.size(),
				"scheduledLessons", schedule.stream().mapToInt(this::duration).sum(),
				"teacherCount", teacherLoads.size(),
				"classroomCount", roomUtilization.size(),
				"highRiskCount", risks.stream().filter(item -> "HIGH".equals(item.get("level"))).count(),
				"riskCount", risks.size()));
		result.put("teacherLoads", teacherLoads);
		result.put("roomUtilization", roomUtilization);
		result.put("distributionRisks", distributionRisks);
		result.put("risks", risks);
		return result;
	}

	@Transactional(readOnly = true)
	public List<String> publishBlockers(String semesterCode) {
		SchedulePolicy policy = policies.resolve(semesterCode);
		Map<String, Object> report = analyze(semesterCode);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> risks = (List<Map<String, Object>>) report.get("risks");
		return risks.stream()
				.filter(item -> blocks(policy, String.valueOf(item.get("type"))))
				.map(item -> String.valueOf(item.get("message")))
				.toList();
	}

	private boolean blocks(SchedulePolicy policy, String type) {
		if ("TEACHER_OVERLOAD".equals(type)) {
			return Boolean.TRUE.equals(policy.getBlockTeacherOverload());
		}
		if ("INCOMPLETE_OFFERING".equals(type)) {
			return Boolean.TRUE.equals(policy.getBlockIncompleteOfferings());
		}
		return Boolean.TRUE.equals(policy.getBlockHardConflicts())
				&& Set.of("ROOM_CONFLICT", "TEACHER_CONFLICT", "STUDENT_CONFLICT")
						.contains(type);
	}

	/**
	 * 发布审计独立于保存校验执行，能够发现旧数据、人工 SQL 或历史迁移绕过写接口后
	 * 留下的教室、教师和学生时间冲突。
	 */
	private List<Map<String, Object>> hardConflictRisks(
			List<ScheduleEntry> schedule,
			Map<String, CourseOffering> offeringById) {
		if (offeringById.isEmpty()) {
			return List.of();
		}
		Map<String, Set<String>> studentsByOffering = members
				.findByOfferingIdInAndEnrollmentStatus(
						new ArrayList<>(offeringById.keySet()),
						"ENROLLED")
				.stream()
				.collect(Collectors.groupingBy(
						TeachingClassMember::getOfferingId,
						Collectors.mapping(TeachingClassMember::getStudentId, Collectors.toSet())));
		List<Map<String, Object>> result = new ArrayList<>();
		Set<String> reported = new HashSet<>();
		Map<Integer, List<ScheduleEntry>> byDay = schedule.stream()
				.collect(Collectors.groupingBy(ScheduleEntry::getDayOfWeek));
		for (List<ScheduleEntry> dayEntries : byDay.values()) {
			for (int leftIndex = 0; leftIndex < dayEntries.size(); leftIndex++) {
				for (int rightIndex = leftIndex + 1; rightIndex < dayEntries.size(); rightIndex++) {
					ScheduleEntry left = dayEntries.get(leftIndex);
					ScheduleEntry right = dayEntries.get(rightIndex);
					if (!timeOverlaps(left, right)) {
						continue;
					}
					CourseOffering leftOffering = offeringById.get(left.getOfferingId());
					CourseOffering rightOffering = offeringById.get(right.getOfferingId());
					if (leftOffering == null || rightOffering == null) {
						continue;
					}
					String slot = "星期" + left.getDayOfWeek() + " 第" + left.getPeriodNo() + "节附近";
					if (left.getClassroomId().equals(right.getClassroomId())) {
						addUniqueRisk(result, reported, "ROOM_CONFLICT", left.getId(), right.getId(),
								"教室在" + slot + "存在重复占用");
					}
					if (leftOffering.getTeacherId().equals(rightOffering.getTeacherId())) {
						addUniqueRisk(result, reported, "TEACHER_CONFLICT", left.getId(), right.getId(),
								leftOffering.getTeacherName() + "在" + slot + "存在重复授课");
					}
					Set<String> sharedStudents = new HashSet<>(
							studentsByOffering.getOrDefault(left.getOfferingId(), Set.of()));
					sharedStudents.retainAll(studentsByOffering.getOrDefault(right.getOfferingId(), Set.of()));
					if (!sharedStudents.isEmpty()) {
						addUniqueRisk(result, reported, "STUDENT_CONFLICT", left.getId(), right.getId(),
								sharedStudents.size() + "名学生在" + slot + "存在课程冲突");
					}
				}
			}
		}
		return result;
	}

	private List<Map<String, Object>> incompleteOfferingRisks(
			List<ScheduleEntry> schedule,
			Map<String, CourseOffering> offeringById) {
		Map<String, Integer> scheduled = schedule.stream()
				.collect(Collectors.groupingBy(
						ScheduleEntry::getOfferingId,
						Collectors.summingInt(this::duration)));
		return offeringById.values().stream()
				.filter(offering -> "ACTIVE".equals(offering.getStatus()))
				.filter(offering -> scheduled.getOrDefault(offering.getId(), 0) < offering.getWeeklyLessons())
				.map(offering -> risk(
						"INCOMPLETE_OFFERING",
						"HIGH",
						offering.getCourseName() + " / " + offering.getTeachingClassName()
								+ " 尚缺 " + (offering.getWeeklyLessons()
								- scheduled.getOrDefault(offering.getId(), 0)) + " 课时"))
				.toList();
	}

	private void addUniqueRisk(
			List<Map<String, Object>> result,
			Set<String> reported,
			String type,
			String leftId,
			String rightId,
			String message) {
		String key = type + ":" + leftId + ":" + rightId;
		if (reported.add(key)) {
			result.add(risk(type, "HIGH", message));
		}
	}

	private boolean timeOverlaps(ScheduleEntry left, ScheduleEntry right) {
		boolean periodOverlap = left.getPeriodNo() <= right.getPeriodNo() + duration(right) - 1
				&& right.getPeriodNo() <= left.getPeriodNo() + duration(left) - 1;
		int leftStartWeek = left.getStartWeek() == null ? 1 : left.getStartWeek();
		int leftEndWeek = left.getEndWeek() == null ? 99 : left.getEndWeek();
		int rightStartWeek = right.getStartWeek() == null ? 1 : right.getStartWeek();
		int rightEndWeek = right.getEndWeek() == null ? 99 : right.getEndWeek();
		boolean weekOverlap = leftStartWeek <= rightEndWeek
				&& rightStartWeek <= leftEndWeek;
		String leftPattern = left.getWeekPattern() == null ? "ALL" : left.getWeekPattern();
		String rightPattern = right.getWeekPattern() == null ? "ALL" : right.getWeekPattern();
		boolean patternOverlap = "ALL".equals(leftPattern)
				|| "ALL".equals(rightPattern)
				|| leftPattern.equals(rightPattern);
		return periodOverlap && weekOverlap && patternOverlap;
	}

	private List<Map<String, Object>> teacherLoads(
			List<ScheduleEntry> schedule,
			Map<String, CourseOffering> offeringById,
			Map<String, TeacherAcademicProfile> teacherById,
			SchedulePolicy policy) {
		Map<String, List<ScheduleEntry>> byTeacher = schedule.stream()
				.filter(entry -> offeringById.containsKey(entry.getOfferingId()))
				.collect(Collectors.groupingBy(entry -> offeringById.get(entry.getOfferingId()).getTeacherId()));
		return byTeacher.entrySet().stream().map(item -> {
			TeacherAcademicProfile teacher = teacherById.get(item.getKey());
			int weekly = item.getValue().stream().mapToInt(this::duration).sum();
			int peakDaily = dayLoads(item.getValue()).values().stream().mapToInt(Integer::intValue).max().orElse(0);
			int longest = item.getValue().stream()
					.collect(Collectors.groupingBy(ScheduleEntry::getDayOfWeek))
					.values().stream().mapToInt(this::longestConsecutive).max().orElse(0);
			int weeklyLimit = teacher == null || teacher.getMaxWeeklyLessons() == null
					? policy.getDefaultMaxWeeklyLessons() : teacher.getMaxWeeklyLessons();
			int dailyLimit = teacher == null || teacher.getMaxDailyLessons() == null
					? policy.getDefaultMaxDailyLessons() : teacher.getMaxDailyLessons();
			int consecutiveLimit = teacher == null || teacher.getMaxConsecutiveLessons() == null
					? policy.getDefaultMaxConsecutiveLessons() : teacher.getMaxConsecutiveLessons();
			long campusSwitchDays = campusSwitchDays(item.getValue(), offeringById);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("teacherId", item.getKey());
			row.put("teacherName", teacher == null ? offeringById.get(item.getValue().getFirst().getOfferingId()).getTeacherName() : teacher.getTeacherName());
			row.put("weeklyLessons", weekly);
			row.put("weeklyLimit", weeklyLimit);
			row.put("peakDailyLessons", peakDaily);
			row.put("dailyLimit", dailyLimit);
			row.put("longestConsecutive", longest);
			row.put("consecutiveLimit", consecutiveLimit);
			row.put("campusSwitchDays", campusSwitchDays);
			row.put("loadRate", weeklyLimit == 0 ? 0 : Math.round(weekly * 1000.0 / weeklyLimit) / 10.0);
			row.put("overloaded", weekly > weeklyLimit || peakDaily > dailyLimit || longest > consecutiveLimit);
			return row;
		}).sorted(Comparator.comparing(row -> String.valueOf(row.get("teacherName")))).toList();
	}

	private List<Map<String, Object>> roomUtilization(
			String semesterCode,
			List<ScheduleEntry> schedule,
			Map<String, Classroom> classroomById) {
		AcademicTerm term = terms.findByTermCode(semesterCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		Map<LocalDate, AcademicCalendarDay> exceptions = calendarDays
				.findByAcademicTermIdOrderByCalendarDate(term.getId()).stream()
				.collect(Collectors.toMap(AcademicCalendarDay::getCalendarDate, item -> item));
		List<ClassroomUnavailableSlot> unavailable = unavailableSlots
				.findBySemesterCodeOrderByClassroomIdAscDayOfWeekAscStartPeriodAsc(semesterCode)
				.stream()
				.filter(item -> "ACTIVE".equals(item.getStatus()))
				.toList();
		Map<String, List<ScheduleEntry>> entriesByRoom = schedule.stream()
				.collect(Collectors.groupingBy(ScheduleEntry::getClassroomId));
		return classroomById.entrySet().stream().map(item -> {
					Classroom room = item.getValue();
					Set<Integer> periods = schedulablePeriods(term, room);
					int available = availableRoomOccurrences(
							term, exceptions, item.getKey(), periods, unavailable);
					int occupied = entriesByRoom.getOrDefault(item.getKey(), List.of()).stream()
							.mapToInt(entry -> entryOccurrences(entry, term, exceptions))
							.sum();
					return Map.<String, Object>of(
							"classroomId", item.getKey(),
							"classroomName", room == null ? item.getKey() : room.getRoomName(),
							"occupiedLessons", occupied,
							"availableLessons", available,
							"utilizationRate", available == 0
									? 0 : Math.round(occupied * 1000.0 / available) / 10.0);
				}).sorted(Comparator.comparing(row -> String.valueOf(row.get("classroomName")))).toList();
	}

	private Set<Integer> schedulablePeriods(AcademicTerm term, Classroom room) {
		BellSchedule schedule = room == null ? null : bellSchedules
				.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
						term.getId(), room.getCampusId(), "ACTIVE")
				.orElse(null);
		if (schedule == null) {
			return java.util.stream.IntStream.rangeClosed(1, 8)
					.boxed()
					.collect(Collectors.toSet());
		}
		return bellPeriods.findByBellScheduleIdOrderByPeriodNo(schedule.getId()).stream()
				.filter(period -> Boolean.TRUE.equals(period.getSchedulable()))
				.map(BellPeriod::getPeriodNo)
				.collect(Collectors.toSet());
	}

	private int availableRoomOccurrences(
			AcademicTerm term,
			Map<LocalDate, AcademicCalendarDay> exceptions,
			String classroomId,
			Set<Integer> periods,
			List<ClassroomUnavailableSlot> unavailable) {
		int result = 0;
		for (LocalDate date = term.getStartDate(); !date.isAfter(term.getEndDate()); date = date.plusDays(1)) {
			if (!isTeachingDate(date, exceptions)) {
				continue;
			}
			int day = date.getDayOfWeek().getValue();
			for (Integer period : periods) {
				boolean blocked = unavailable.stream()
						.filter(item -> classroomId.equals(item.getClassroomId()))
						.anyMatch(item -> item.getDayOfWeek() == day
								&& period >= item.getStartPeriod()
								&& period <= item.getEndPeriod());
				if (!blocked) {
					result++;
				}
			}
		}
		return result;
	}

	private int entryOccurrences(
			ScheduleEntry entry,
			AcademicTerm term,
			Map<LocalDate, AcademicCalendarDay> exceptions) {
		int result = 0;
		for (LocalDate date = term.getStartDate(); !date.isAfter(term.getEndDate()); date = date.plusDays(1)) {
			if (date.getDayOfWeek().getValue() != entry.getDayOfWeek()
					|| !isTeachingDate(date, exceptions)) {
				continue;
			}
			int week = (int) (ChronoUnit.DAYS.between(term.getStartDate(), date) / 7) + 1;
			int startWeek = entry.getStartWeek() == null ? 1 : entry.getStartWeek();
			int endWeek = entry.getEndWeek() == null ? term.getWeekCount() : entry.getEndWeek();
			if (week >= startWeek && week <= endWeek && matchesPattern(entry.getWeekPattern(), week)) {
				result += duration(entry);
			}
		}
		return result;
	}

	private boolean isTeachingDate(
			LocalDate date,
			Map<LocalDate, AcademicCalendarDay> exceptions) {
		AcademicCalendarDay configured = exceptions.get(date);
		return configured == null
				? date.getDayOfWeek().getValue() <= 5
				: Boolean.TRUE.equals(configured.getTeachingDay());
	}

	private boolean matchesPattern(String pattern, int week) {
		return pattern == null
				|| "ALL".equals(pattern)
				|| ("ODD".equals(pattern) && week % 2 == 1)
				|| ("EVEN".equals(pattern) && week % 2 == 0);
	}

	private List<Map<String, Object>> distributionRisks(
			List<ScheduleEntry> schedule,
			Map<String, CourseOffering> offeringById,
			int concentrationThreshold) {
		List<Map<String, Object>> result = new ArrayList<>();
		schedule.stream().collect(Collectors.groupingBy(ScheduleEntry::getOfferingId)).forEach((offeringId, values) -> {
			Map<Integer, Integer> days = dayLoads(values);
			int maximum = days.values().stream().mapToInt(Integer::intValue).max().orElse(0);
			if (maximum > concentrationThreshold) {
				CourseOffering offering = offeringById.get(offeringId);
				result.add(Map.of(
						"offeringId", offeringId,
						"courseName", offering == null ? offeringId : offering.getCourseName(),
						"teachingClassName", offering == null ? "" : offering.getTeachingClassName(),
						"maximumSameDayLessons", maximum));
			}
		});
		return result;
	}

	private Map<Integer, Integer> dayLoads(List<ScheduleEntry> values) {
		Map<Integer, Integer> result = new HashMap<>();
		values.forEach(entry -> result.merge(entry.getDayOfWeek(), duration(entry), Integer::sum));
		return result;
	}

	private int longestConsecutive(List<ScheduleEntry> values) {
		Set<Integer> periods = new HashSet<>();
		values.forEach(entry -> {
			for (int offset = 0; offset < duration(entry); offset++) {
				periods.add(entry.getPeriodNo() + offset);
			}
		});
		int current = 0;
		int maximum = 0;
		for (int period = 1; period <= 20; period++) {
			current = periods.contains(period) ? current + 1 : 0;
			maximum = Math.max(maximum, current);
		}
		return maximum;
	}

	private long campusSwitchDays(
			List<ScheduleEntry> values,
			Map<String, CourseOffering> offeringById) {
		return values.stream().collect(Collectors.groupingBy(ScheduleEntry::getDayOfWeek))
				.values().stream()
				.filter(day -> day.stream()
						.map(entry -> offeringById.get(entry.getOfferingId()))
						.filter(java.util.Objects::nonNull)
						.map(CourseOffering::getCampusId)
						.filter(java.util.Objects::nonNull)
						.distinct().count() > 1)
				.count();
	}

	private int duration(ScheduleEntry entry) {
		return Math.max(entry.getDurationPeriods() == null ? 1 : entry.getDurationPeriods(), 1);
	}

	private Map<String, Object> risk(String type, String level, String message) {
		return Map.of("type", type, "level", level, "message", message);
	}
}
