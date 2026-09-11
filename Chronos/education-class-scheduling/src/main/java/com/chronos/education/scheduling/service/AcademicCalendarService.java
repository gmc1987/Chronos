package com.chronos.education.scheduling.service;

import java.util.List;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.scheduling.dao.AcademicCalendarDayRepository;
import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.dao.BellPeriodRepository;
import com.chronos.education.scheduling.dao.BellScheduleRepository;
import com.chronos.education.scheduling.model.AcademicCalendarDay;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.model.BellPeriod;
import com.chronos.education.scheduling.model.BellSchedule;
import com.chronos.education.scheduling.model.BellScheduleView;

/** 教学校历与作息服务，集中保证日期、默认方案和节次时间的业务一致性。 */
@Service
public class AcademicCalendarService {
	private final AcademicTermRepository terms;
	private final AcademicCalendarDayRepository calendarDays;
	private final BellScheduleRepository bellSchedules;
	private final BellPeriodRepository bellPeriods;

	public AcademicCalendarService(
			AcademicTermRepository terms,
			AcademicCalendarDayRepository calendarDays,
			BellScheduleRepository bellSchedules,
			BellPeriodRepository bellPeriods) {
		this.terms = terms;
		this.calendarDays = calendarDays;
		this.bellSchedules = bellSchedules;
		this.bellPeriods = bellPeriods;
	}

	public List<AcademicCalendarDay> calendarDays(String termId) {
		requireTerm(termId);
		return calendarDays.findByAcademicTermIdOrderByCalendarDate(termId);
	}

	public java.util.Map<String, Object> termProgress(String termCode, LocalDate date) {
		AcademicTerm term = terms.findByTermCode(termCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		LocalDate target = date == null ? LocalDate.now() : date;
		boolean inTerm = !target.isBefore(term.getStartDate()) && !target.isAfter(term.getEndDate());
		int week = inTerm
				? (int) (ChronoUnit.DAYS.between(term.getStartDate(), target) / 7) + 1
				: 0;
		return java.util.Map.of(
				"termId", term.getId(),
				"termCode", term.getTermCode(),
				"date", target,
				"inTerm", inTerm,
				"teachingWeek", week,
				"weekCount", term.getWeekCount());
	}

	/** 校验指定校区的排课节次，兼容尚未建立作息方案的历史学期。 */
	public void validateSchedulingSlot(
			String termCode,
			String campusId,
			Integer periodNo,
			int duration) {
		AcademicTerm term = terms.findByTermCode(termCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (!bellSchedules.existsByAcademicTermIdAndStatus(term.getId(), "ACTIVE")) {
			return;
		}
		BellSchedule schedule = bellSchedules
				.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
						term.getId(),
						campusId,
						"ACTIVE")
				.orElseThrow(() -> new IllegalStateException("当前校区尚未配置默认作息方案"));
		java.util.Set<Integer> allowed = bellPeriods
				.findByBellScheduleIdOrderByPeriodNo(schedule.getId()).stream()
				.filter(item -> Boolean.TRUE.equals(item.getSchedulable()))
				.map(BellPeriod::getPeriodNo)
				.collect(java.util.stream.Collectors.toSet());
		for (int value = periodNo; value < periodNo + duration; value++) {
			if (!allowed.contains(value)) {
				throw new IllegalStateException("所选节次不在校区作息方案的可排课范围内");
			}
		}
	}

	public java.util.Set<Integer> schedulablePeriodNumbers(
			String termCode,
			String campusId,
			int fallbackMaximum) {
		AcademicTerm term = terms.findByTermCode(termCode)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
		if (!bellSchedules.existsByAcademicTermIdAndStatus(term.getId(), "ACTIVE")) {
			return java.util.stream.IntStream.rangeClosed(1, fallbackMaximum)
					.boxed()
					.collect(java.util.stream.Collectors.toSet());
		}
		BellSchedule schedule = bellSchedules
				.findFirstByAcademicTermIdAndCampusIdAndDefaultScheduleTrueAndStatus(
						term.getId(), campusId, "ACTIVE")
				.orElseThrow(() -> new IllegalStateException("教学任务所属校区尚未配置默认作息方案"));
		return bellPeriods.findByBellScheduleIdOrderByPeriodNo(schedule.getId()).stream()
				.filter(item -> Boolean.TRUE.equals(item.getSchedulable()))
				.map(BellPeriod::getPeriodNo)
				.collect(java.util.stream.Collectors.toSet());
	}

	@Transactional
	public AcademicCalendarDay saveCalendarDay(String id, AcademicCalendarDay command) {
		AcademicTerm term = requireTerm(command.getAcademicTermId());
		if (command.getCalendarDate() == null
				|| command.getCalendarDate().isBefore(term.getStartDate())
				|| command.getCalendarDate().isAfter(term.getEndDate())) {
			throw new IllegalArgumentException("特殊日期必须位于学期起止日期范围内");
		}
		String currentId = id == null ? "" : id;
		if (calendarDays.existsByAcademicTermIdAndCalendarDateAndIdNot(
				command.getAcademicTermId(),
				command.getCalendarDate(),
				currentId)) {
			throw new IllegalArgumentException("该日期已经配置");
		}
		AcademicCalendarDay value = id == null
				? new AcademicCalendarDay()
				: calendarDays.findById(id)
						.orElseThrow(() -> new IllegalArgumentException("校历日期不存在"));
		value.setAcademicTermId(command.getAcademicTermId());
		value.setCalendarDate(command.getCalendarDate());
		value.setDayType(required(command.getDayType(), "日期类型不能为空"));
		value.setDayName(required(command.getDayName(), "日期名称不能为空"));
		value.setTeachingDay(Boolean.TRUE.equals(command.getTeachingDay()));
		value.setRemark(command.getRemark());
		return calendarDays.save(value);
	}

	@Transactional
	public void deleteCalendarDay(String id) {
		calendarDays.deleteById(id);
	}

	public List<BellScheduleView> bellSchedules(String termId) {
		requireTerm(termId);
		return bellSchedules.findByAcademicTermIdOrderByScheduleName(termId).stream()
				.map(schedule -> new BellScheduleView(
						schedule,
						bellPeriods.findByBellScheduleIdOrderByPeriodNo(schedule.getId())))
				.toList();
	}

	@Transactional
	public BellSchedule saveBellSchedule(String id, BellSchedule command) {
		requireTerm(command.getAcademicTermId());
		BellSchedule value = id == null
				? new BellSchedule()
				: bellSchedules.findById(id)
						.orElseThrow(() -> new IllegalArgumentException("作息方案不存在"));
		value.setScheduleCode(required(command.getScheduleCode(), "方案编码不能为空"));
		value.setScheduleName(required(command.getScheduleName(), "方案名称不能为空"));
		value.setAcademicTermId(command.getAcademicTermId());
		value.setCampusId(required(command.getCampusId(), "校区不能为空"));
		value.setDefaultSchedule(Boolean.TRUE.equals(command.getDefaultSchedule()));
		value.setStatus(command.getStatus() == null ? "ACTIVE" : command.getStatus());
		if (Boolean.TRUE.equals(value.getDefaultSchedule())) {
			// 同一学期、同一校区只能有一个默认作息，避免排课时产生歧义。
			bellSchedules.findByAcademicTermIdAndCampusIdAndDefaultScheduleTrue(
					command.getAcademicTermId(),
					command.getCampusId()).stream()
					.filter(item -> !item.getId().equals(value.getId()))
					.forEach(item -> {
						item.setDefaultSchedule(false);
						bellSchedules.save(item);
					});
		}
		return bellSchedules.save(value);
	}

	@Transactional
	public void deleteBellSchedule(String id) {
		bellPeriods.deleteByBellScheduleId(id);
		bellSchedules.deleteById(id);
	}

	@Transactional
	public BellPeriod saveBellPeriod(String id, BellPeriod command) {
		bellSchedules.findById(command.getBellScheduleId())
				.orElseThrow(() -> new IllegalArgumentException("作息方案不存在"));
		if (command.getPeriodNo() == null || command.getPeriodNo() < 1) {
			throw new IllegalArgumentException("节次序号必须大于 0");
		}
		if (command.getStartTime() == null
				|| command.getEndTime() == null
				|| !command.getStartTime().isBefore(command.getEndTime())) {
			throw new IllegalArgumentException("节次开始时间必须早于结束时间");
		}
		BellPeriod value = id == null
				? new BellPeriod()
				: bellPeriods.findById(id)
						.orElseThrow(() -> new IllegalArgumentException("节次不存在"));
		value.setBellScheduleId(command.getBellScheduleId());
		value.setPeriodNo(command.getPeriodNo());
		value.setPeriodName(required(command.getPeriodName(), "节次名称不能为空"));
		value.setDaySegment(required(command.getDaySegment(), "时段不能为空"));
		value.setStartTime(command.getStartTime());
		value.setEndTime(command.getEndTime());
		value.setSchedulable(!Boolean.FALSE.equals(command.getSchedulable()));
		return bellPeriods.save(value);
	}

	@Transactional
	public void deleteBellPeriod(String id) {
		bellPeriods.deleteById(id);
	}

	private AcademicTerm requireTerm(String id) {
		return terms.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("学期不存在"));
	}

	private String required(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value.trim();
	}
}
