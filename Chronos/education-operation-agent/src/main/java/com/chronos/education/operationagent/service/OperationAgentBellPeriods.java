package com.chronos.education.operationagent.service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts the model's structured scheduling rules into a reviewable timetable. */
final class OperationAgentBellPeriods {
	private OperationAgentBellPeriods() {}

	static List<Map<String, Object>> generate(Map<String, Object> rules) {
		int count = minutes(rules, "periodCount", 8, 1, 24);
		int morning = minutes(rules, "morningCount", (count + 1) / 2, 0, count);
		int gap = minutes(rules, "breakMinutes", 10, 0, 180);
		boolean split = morning > 0 && morning < count;
		int lunch = split ? minutes(rules, "lunchMinutes", 90, gap, 300) : 0;
		int breaks = gap * (count - (split ? 2 : 1)) + lunch;
		LocalTime start = time(rules, "startTime", LocalTime.of(9, 0));
		boolean fixedLength = rules.get("periodMinutes") != null;
		LocalTime end = time(rules, "endTime", fixedLength ? null : LocalTime.of(17, 30));
		int length = minutes(rules, "periodMinutes", 45, 1, 180);
		int startSecond = start.toSecondOfDay();
		int available = end == null ? count * length * 60 : end.toSecondOfDay() - startSecond - breaks * 60;
		if (available < count * 60 || (fixedLength && available != count * length * 60)
				|| startSecond + available + breaks * 60 >= 86400) {
			throw new IllegalArgumentException("作息时间与节数、课长或休息时长不一致，请调整其中一项");
		}

		List<Map<String, Object>> periods = new ArrayList<>();
		int cursor = startSecond;
		for (int index = 0; index < count; index++) {
			int periodEnd = cursor + available / count + (index < available % count ? 1 : 0);
			periods.add(Map.of(
					"periodNo", index + 1, "periodName", "第" + (index + 1) + "节",
					"daySegment", index < morning ? "MORNING" : "AFTERNOON",
					"startTime", LocalTime.ofSecondOfDay(cursor).toString(),
					"endTime", LocalTime.ofSecondOfDay(periodEnd).toString()));
			cursor = periodEnd + (split && index + 1 == morning ? lunch : gap) * 60;
		}
		return periods;
	}

	private static int minutes(Map<String, Object> rules, String field, int fallback, int minimum, int maximum) {
		Object raw = rules.get(field);
		int value = fallback;
		if (raw != null) {
			try {
				value = Integer.parseInt(raw.toString());
			} catch (NumberFormatException exception) {
				throw new IllegalArgumentException("作息规则 " + field + " 必须是整数");
			}
		}
		if (value < minimum || value > maximum) {
			throw new IllegalArgumentException("作息规则 " + field + " 超出允许范围");
		}
		return value;
	}

	private static LocalTime time(Map<String, Object> rules, String field, LocalTime fallback) {
		Object raw = rules.get(field);
		if (raw == null) return fallback;
		try {
			return LocalTime.parse(raw.toString());
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("作息起止时间请使用 HH:mm 格式");
		}
	}
}
