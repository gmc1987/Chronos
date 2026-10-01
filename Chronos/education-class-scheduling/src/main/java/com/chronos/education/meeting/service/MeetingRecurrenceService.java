package com.chronos.education.meeting.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.education.meeting.dao.MeetingRepository;
import com.chronos.education.meeting.dao.MeetingSeriesRepository;
import com.chronos.education.meeting.model.Meeting;
import com.chronos.education.meeting.model.MeetingCommands;
import com.chronos.education.meeting.model.MeetingRecurrenceView;
import com.chronos.education.meeting.model.MeetingSeries;
import com.chronos.education.meeting.model.MeetingView;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MeetingRecurrenceService {
	private static final int MAX_OCCURRENCES = 400;
	private final MeetingRepository meetings;
	private final MeetingSeriesRepository series;
	private final MeetingCenterService meetingCenter;
	private final CalendarIntegrationService calendar;

	@Transactional(readOnly = true)
	public MeetingRecurrenceView.Preview preview(MeetingCommands.RecurrencePreview command) {
		Rule rule = rule(command);
		return toPreview(rule, occurrences(rule));
	}

	@Transactional
	public MeetingView create(
			MeetingCommands.RecurringSave command,
			String username) {
		if (command == null || command.meeting() == null || command.recurrence() == null) {
			throw new IllegalArgumentException("周期会议参数不能为空");
		}
		Rule rule = rule(command.recurrence());
		List<MeetingRecurrenceView.Occurrence> occurrences = occurrences(rule);
		if (occurrences.isEmpty()) {
			throw new IllegalArgumentException("周期规则没有可创建的会议实例");
		}
		Meeting root = null;
		for (int i = 0; i < occurrences.size(); i++) {
			MeetingRecurrenceView.Occurrence occurrence = occurrences.get(i);
			MeetingCommands.Save save = withTimes(
					command.meeting(), occurrence.startTime(), occurrence.endTime());
			MeetingView view = meetingCenter.saveMeeting(null, save, username);
			Meeting meeting = meetings.findById(view.meeting().getId()).orElseThrow();
			if (root == null) {
				root = meeting;
			}
			meeting.setSeriesId(root.getId());
			meeting.setOccurrenceKey(occurrence.occurrenceKey());
			meeting.setExceptionType("NONE");
			meeting.setRecurrenceFrequency(rule.frequency());
			meeting.setRecurrenceInterval(rule.interval());
			meeting.setRecurrenceByDay(rule.byDay());
			meeting.setRecurrenceDayOfMonth(rule.dayOfMonth());
			meeting.setRecurrenceUntil(rule.until());
			meeting.setRecurrenceCount(rule.count());
			meetings.save(meeting);
			calendar.enqueue(meeting, "UPSERT");
		}
		MeetingSeries metadata = new MeetingSeries();
		metadata.setId(root.getId());
		metadata.setOrganizerUsername(username);
		metadata.setTitle(command.meeting().title().trim());
		metadata.setRecurrenceRule(rule.frequency()
				+ ";INTERVAL=" + rule.interval()
				+ ";BYDAY=" + rule.byDay()
				+ ";BYMONTHDAY=" + rule.dayOfMonth());
		metadata.setStartAt(rule.start());
		metadata.setEndAt(rule.start().plus(rule.duration()));
		metadata.setUntilAt(rule.until());
		metadata.setOccurrenceLimit(rule.count());
		metadata.setStatus("DRAFT");
		series.save(metadata);
		String rootId = root.getId();
		return meetings.findById(rootId)
				.map(meetingCenter::viewForRecurrence)
				.orElseThrow();
	}

	@Transactional(readOnly = true)
	public List<MeetingView> instances(String id, String username) {
		Meeting current = require(id);
		requireOrganizer(current, username);
		List<Meeting> values = current.getSeriesId() == null
				? List.of(current)
				: meetings.findBySeriesIdOrderByStartTimeAsc(current.getSeriesId());
		return values.stream()
				.map(meetingCenter::viewForRecurrence)
				.toList();
	}

	@Transactional
	public MeetingView change(
			String id,
			MeetingCommands.SeriesChange command,
			String username) {
		Meeting current = require(id);
		requireOrganizer(current, username);
		if (command == null || command.meeting() == null) {
			throw new IllegalArgumentException("周期会议变更内容不能为空");
		}
		String mode = command.mode().trim().toUpperCase(Locale.ROOT);
		if (!List.of("THIS_ONLY", "THIS_AND_FUTURE").contains(mode)) {
			throw new IllegalArgumentException("变更范围只允许 THIS_ONLY 或 THIS_AND_FUTURE");
		}
		String seriesId = current.getSeriesId() == null ? current.getId() : current.getSeriesId();
		List<Meeting> targets = "THIS_ONLY".equals(mode)
				? List.of(current)
				: meetings.findBySeriesIdAndStartTimeGreaterThanEqualOrderByStartTimeAsc(
						seriesId, current.getStartTime());
		DurationShift shift = new DurationShift(
				Duration.between(current.getStartTime(), command.meeting().startTime()),
				Duration.between(command.meeting().startTime(), command.meeting().endTime()));
		MeetingView result = null;
		for (Meeting target : targets) {
			LocalDateTime start = "THIS_ONLY".equals(mode)
					? command.meeting().startTime()
					: target.getStartTime().plus(shift.startDelta());
			LocalDateTime end = "THIS_ONLY".equals(mode)
					? command.meeting().endTime()
					: start.plus(shift.duration());
			result = meetingCenter.saveMeeting(
					target.getId(),
					withTimes(
							command.meeting(),
							start,
							end,
							"THIS_ONLY".equals(mode)
									? command.meeting().recordVersion()
									: target.getRecordVersion()),
					username);
			target = meetings.findById(target.getId()).orElseThrow();
			target.setExceptionType("MODIFIED".equals(target.getExceptionType())
					? "MODIFIED" : ("THIS_ONLY".equals(mode) ? "MODIFIED" : "NONE"));
			meetings.save(target);
			calendar.enqueue(target, "UPSERT");
		}
		return result;
	}

	@Transactional
	public void cancelSeries(String id, String username) {
		Meeting current = require(id);
		requireOrganizer(current, username);
		String seriesId = current.getSeriesId();
		LocalDateTime now = LocalDateTime.now();
		List<Meeting> values = seriesId == null
				? List.of(current)
				: meetings.findBySeriesIdOrderByStartTimeAsc(seriesId);
		for (Meeting meeting : values) {
			if (meeting.getStartTime().isBefore(now)
					|| "CANCELLED".equals(meeting.getStatus())) {
				continue;
			}
			meeting.setStatus("CANCELLED");
			meeting.setCancelledAt(now);
			meeting.setCancelReason("周期会议系列取消");
			meetings.save(meeting);
			calendar.enqueue(meeting, "CANCEL");
		}
		if (seriesId != null) {
			series.findById(seriesId).ifPresent(value -> {
				value.setStatus("CANCELLED");
				series.save(value);
			});
		}
	}

	private Rule rule(MeetingCommands.RecurrencePreview command) {
		if (command == null || command.startTime() == null || command.endTime() == null
				|| !command.endTime().isAfter(command.startTime())) {
			throw new IllegalArgumentException("请填写有效的周期会议起止时间");
		}
		String frequency = command.frequency().trim().toUpperCase(Locale.ROOT);
		if (!List.of("WEEKLY", "MONTHLY").contains(frequency)) {
			throw new IllegalArgumentException("周期频率只支持 WEEKLY 或 MONTHLY");
		}
		int interval = command.interval() == null ? 1 : command.interval();
		if (interval < 1 || interval > 52) {
			throw new IllegalArgumentException("周期间隔必须在1到52之间");
		}
		if (command.until() == null && (command.count() == null || command.count() < 1)) {
			throw new IllegalArgumentException("周期会议必须设置结束日期或次数");
		}
		if (command.until() != null && command.until().isBefore(command.startTime())) {
			throw new IllegalArgumentException("结束日期不能早于首次会议");
		}
		if (command.count() != null && (command.count() < 1 || command.count() > MAX_OCCURRENCES)) {
			throw new IllegalArgumentException("周期次数必须在1到400之间");
		}
		Integer dayOfMonth = command.dayOfMonth();
		if ("MONTHLY".equals(frequency)
				&& dayOfMonth != null && (dayOfMonth < 1 || dayOfMonth > 31)) {
			throw new IllegalArgumentException("每月日期必须在1到31之间");
		}
		int effectiveDayOfMonth = "MONTHLY".equals(frequency)
				? (dayOfMonth == null ? command.startTime().getDayOfMonth() : dayOfMonth)
				: 1;
		return new Rule(
				frequency,
				interval,
				normalizeDays(command.byDay()),
				effectiveDayOfMonth,
				command.startTime(),
				Duration.between(command.startTime(), command.endTime()),
				command.until(),
				command.count());
	}

	private List<MeetingRecurrenceView.Occurrence> occurrences(Rule rule) {
		List<MeetingRecurrenceView.Occurrence> result = new ArrayList<>();
		LocalDateTime cursor = rule.start();
		int ordinal = 1;
		while (result.size() < MAX_OCCURRENCES) {
			if (rule.until() != null && cursor.isAfter(rule.until())) {
				break;
			}
			if (rule.count() != null && result.size() >= rule.count()) {
				break;
			}
			if ("WEEKLY".equals(rule.frequency()) && !matchesWeekday(cursor, rule.byDay())) {
				cursor = cursor.plusDays(1);
				continue;
			}
			LocalDateTime end = cursor.plus(rule.duration());
			result.add(new MeetingRecurrenceView.Occurrence(
					cursor.toString(), cursor, end, ordinal++));
			if ("MONTHLY".equals(rule.frequency())) {
				cursor = nextMonth(rule, cursor);
			} else {
				cursor = cursor.plusDays(1);
				if (!hasAnotherWeeklyDay(cursor, rule)) {
					cursor = cursor.with(TemporalAdjusters.nextOrSame(
							parseDay(rule.byDay().split(",")[0]))).plusWeeks(rule.interval() - 1L);
				}
			}
		}
		return result;
	}

	private LocalDateTime nextMonth(Rule rule, LocalDateTime current) {
		YearMonth month = YearMonth.from(current.plusMonths(rule.interval()));
		int day = Math.min(rule.dayOfMonth(), month.lengthOfMonth());
		return LocalDateTime.of(month.atDay(day), rule.start().toLocalTime());
	}

	private boolean matchesWeekday(LocalDateTime value, String byDay) {
		return List.of(byDay.split(",")).contains(value.getDayOfWeek().name().substring(0, 2));
	}

	private boolean hasAnotherWeeklyDay(LocalDateTime value, Rule rule) {
		if (rule.byDay().indexOf(',') < 0) return false;
		return matchesWeekday(value, rule.byDay());
	}

	private String normalizeDays(String value) {
		if (value == null || value.isBlank()) return "MO";
		return List.of(value.toUpperCase(Locale.ROOT).split(",")).stream()
				.map(String::trim)
				.filter(day -> List.of("MO", "TU", "WE", "TH", "FR", "SA", "SU").contains(day))
				.distinct()
				.sorted()
				.reduce((left, right) -> left + "," + right)
				.orElse("MO");
	}

	private DayOfWeek parseDay(String value) {
		return switch (value) {
			case "MO" -> DayOfWeek.MONDAY;
			case "TU" -> DayOfWeek.TUESDAY;
			case "WE" -> DayOfWeek.WEDNESDAY;
			case "TH" -> DayOfWeek.THURSDAY;
			case "FR" -> DayOfWeek.FRIDAY;
			case "SA" -> DayOfWeek.SATURDAY;
			default -> DayOfWeek.SUNDAY;
		};
	}

	private MeetingRecurrenceView.Preview toPreview(
			Rule rule, List<MeetingRecurrenceView.Occurrence> occurrences) {
		return new MeetingRecurrenceView.Preview(
				rule.frequency(), rule.interval(), rule.byDay(), rule.dayOfMonth(),
				rule.until(), rule.count(), occurrences);
	}

	private MeetingCommands.Save withTimes(
			MeetingCommands.Save source, LocalDateTime start, LocalDateTime end) {
		return withTimes(source, start, end, source.recordVersion());
	}

	private MeetingCommands.Save withTimes(
			MeetingCommands.Save source,
			LocalDateTime start,
			LocalDateTime end,
			Long recordVersion) {
		return new MeetingCommands.Save(
				source.title(), source.agenda(), source.meetingType(), start, end,
				source.roomId(), source.meetingProvider(), source.externalMeetingId(),
				source.joinUrl(), source.onlineAccessCode(), source.participantUsernames(),
				recordVersion);
	}

	private Meeting require(String id) {
		return meetings.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("会议不存在"));
	}

	private void requireOrganizer(Meeting meeting, String username) {
		if (!username.equals(meeting.getOrganizerUsername())) {
			throw new AccessDeniedException("只有会议组织者可以管理周期会议");
		}
	}

	private record Rule(
			String frequency,
			int interval,
			String byDay,
			int dayOfMonth,
			LocalDateTime start,
			java.time.Duration duration,
			LocalDateTime until,
			Integer count) {
	}

	private record DurationShift(java.time.Duration startDelta, java.time.Duration duration) {
	}
}
