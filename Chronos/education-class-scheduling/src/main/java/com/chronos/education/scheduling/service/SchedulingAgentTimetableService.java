package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.CourseOffering;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Maps relative time phrases to authoritative, campus-specific timetable periods. */
@Service
@Transactional(readOnly = true)
public class SchedulingAgentTimetableService {
	private final AcademicTermRepository terms;
	private final CourseOfferingRepository offerings;
	private final BellScheduleRepository schedules;
	private final BellPeriodRepository periods;

	public SchedulingAgentTimetableService(AcademicTermRepository terms,
			CourseOfferingRepository offerings, BellScheduleRepository schedules,
			BellPeriodRepository periods) {
		this.terms = terms;
		this.offerings = offerings;
		this.schedules = schedules;
		this.periods = periods;
	}

	public SegmentResolution segment(String semesterCode, String teacherId,
			String mode, Set<String> selectedOfferingIds, String daySegment) {
		AcademicTerm term = activeTerm(semesterCode);
		Set<String> campuses = activeOfferings(semesterCode, mode, selectedOfferingIds).stream()
				.filter(offering -> teacherId.equals(offering.getTeacherId()))
				.map(CourseOffering::getCampusId)
				.collect(java.util.stream.Collectors.toSet());
		if (campuses.size() != 1
				|| campuses.stream().anyMatch(campus -> campus == null || campus.isBlank())) {
			return new SegmentResolution(List.of(), "教师排课范围没有唯一的校区，无法安全映射上下午");
		}
		var schedule = schedules.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
				term.getId(), campuses.iterator().next(), "ACTIVE");
		if (schedule.isEmpty()) {
			return new SegmentResolution(List.of(), "该教师校区尚未配置启用的默认作息方案");
		}
		List<BellPeriod> segmentPeriods = periods.findByBellScheduleIdOrderByPeriodNo(
				schedule.get().getId()).stream()
				.filter(period -> Boolean.TRUE.equals(period.getSchedulable()))
				.filter(period -> daySegment.equals(period.getDaySegment()))
				.toList();
		if (segmentPeriods.stream().anyMatch(period ->
				period.getPeriodNo() == null || period.getPeriodNo() < 1 || period.getPeriodNo() > 20)) {
			return new SegmentResolution(List.of(), "作息方案包含不受排课求解器支持的节次");
		}
		List<Integer> matched = segmentPeriods.stream()
				.map(BellPeriod::getPeriodNo)
				.distinct().sorted().toList();
		if (matched.isEmpty()) {
			return new SegmentResolution(List.of(), "作息方案没有该时段的可排课节次");
		}
		return new SegmentResolution(matched, null);
	}

	public Dimensions dimensions(String semesterCode, String mode, Set<String> selectedOfferingIds) {
		AcademicTerm term = activeTerm(semesterCode);
		if (term.getWeekCount() == null || term.getWeekCount() < 1 || term.getWeekCount() > 52) {
			throw new IllegalStateException("学期教学周数无效");
		}
		List<CourseOffering> targets = activeOfferings(semesterCode, mode, selectedOfferingIds);
		if (targets.isEmpty()) {
			throw new IllegalStateException("学期范围内没有有效教学任务");
		}
		boolean configured = schedules.existsByAcademicTermIdAndStatus(term.getId(), "ACTIVE");
		int lastPeriod = configured ? 0 : 8;
		if (configured) {
			// 未指定校区的公共课可由求解器选择任一合适校区的教室，不能当成缺少作息的校区。
			Set<String> campuses = targets.stream().map(CourseOffering::getCampusId)
					.filter(campus -> campus != null && !campus.isBlank())
					.collect(java.util.stream.Collectors.toSet());
			for (String campus : campuses) {
				var schedule = schedules.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
						term.getId(), campus, "ACTIVE")
						.orElseThrow(() -> new IllegalStateException("教学任务校区缺少默认作息方案"));
				List<Integer> allowed = periods.findByBellScheduleIdOrderByPeriodNo(schedule.getId())
						.stream().filter(period -> Boolean.TRUE.equals(period.getSchedulable()))
						.map(BellPeriod::getPeriodNo).toList();
				if (allowed.isEmpty()) {
					throw new IllegalStateException("默认作息方案没有可排课节次");
				}
				if (allowed.stream().anyMatch(number ->
						number == null || number < 1 || number > 20)) {
					throw new IllegalStateException("作息节次超过排课求解器支持范围");
				}
				int maximum = allowed.stream().mapToInt(Integer::intValue).max().orElseThrow();
				lastPeriod = Math.max(lastPeriod, maximum);
			}
			if (campuses.isEmpty()) {
				lastPeriod = 8;
			}
		}
		return new Dimensions(5, lastPeriod, term.getWeekCount());
	}

	public Set<String> targetOfferingIds(String semesterCode, String mode, Set<String> selectedOfferingIds) {
		activeTerm(semesterCode);
		return activeOfferings(semesterCode, mode, selectedOfferingIds).stream()
				.map(CourseOffering::getId).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	private AcademicTerm activeTerm(String code) {
		return terms.findByTermCode(code)
				.filter(term -> "ACTIVE".equals(term.getStatus()))
				.orElseThrow(() -> new IllegalArgumentException("学期不存在或未启用"));
	}

	private List<CourseOffering> activeOfferings(String semester, String mode, Set<String> selected) {
		if (mode == null || !Set.of("GLOBAL", "LOCAL").contains(mode) || selected == null) {
			throw new IllegalArgumentException("排课模式无效");
		}
		List<CourseOffering> all = offerings.findBySemesterCodeOrderByOfferingCode(semester)
				.stream().filter(offering -> "ACTIVE".equals(offering.getStatus())).toList();
		if ("GLOBAL".equals(mode)) {
			return all;
		}
		List<CourseOffering> targets = all.stream()
				.filter(offering -> selected.contains(offering.getId())).toList();
		if (targets.size() != selected.size()) {
			throw new IllegalArgumentException("部分教学任务不存在或已停用");
		}
		return targets;
	}

	public record SegmentResolution(List<Integer> periodNumbers, String clarification) {
	}

	public record Dimensions(int weekdays, int periodsPerDay, int endWeek) {
	}
}
