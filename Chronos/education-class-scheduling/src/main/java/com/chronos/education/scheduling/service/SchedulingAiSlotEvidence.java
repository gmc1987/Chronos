package com.chronos.education.scheduling.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class SchedulingAiSlotEvidence {
	private static final Map<String, Integer> DAYS = Map.ofEntries(
			Map.entry("星期一", 1), Map.entry("周一", 1),
			Map.entry("星期二", 2), Map.entry("周二", 2),
			Map.entry("星期三", 3), Map.entry("周三", 3),
			Map.entry("星期四", 4), Map.entry("周四", 4),
			Map.entry("星期五", 5), Map.entry("周五", 5),
			Map.entry("星期六", 6), Map.entry("周六", 6),
			Map.entry("星期日", 7), Map.entry("星期天", 7),
			Map.entry("周日", 7));
	private static final Pattern PERIODS =
			Pattern.compile("第?([一二三四五六七八九十\\d]+)节");
	private static final Pattern DAY_RANGE = Pattern.compile(
			"(?:星期|周)[一二三四五六日天]\\s*(?:到|至|[-—~～])\\s*(?:星期|周)[一二三四五六日天]");
	private static final Pattern PERIOD_RANGE = Pattern.compile(
			"第?[一二三四五六七八九十\\d]+节?\\s*(?:到|至|[-—~～])\\s*第?[一二三四五六七八九十\\d]+节");

	private SchedulingAiSlotEvidence() {
	}

	static Set<Integer> days(String source) {
		return DAYS.entrySet().stream().filter(day -> source.contains(day.getKey()))
				.map(Map.Entry::getValue).collect(Collectors.toSet());
	}

	static Set<Integer> periods(String source) {
		return PERIODS.matcher(source).results().map(match -> number(match.group(1)))
				.collect(Collectors.toSet());
	}

	static boolean forbidden(String source) {
		return List.of("不能", "不可", "禁止", "禁排", "不排", "不要", "避免").stream()
				.anyMatch(source::contains)
				&& List.of("尽量", "优先", "希望", "最好").stream()
						.noneMatch(source::contains)
				&& List.of("除了", "除外", "例外", "但是", "但允许", "除去").stream()
						.noneMatch(source::contains)
				&& !DAY_RANGE.matcher(source).find()
				&& !PERIOD_RANGE.matcher(source).find();
	}

	private static Integer number(String value) {
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException ignored) {
			List<String> digits = List.of("一", "二", "三", "四", "五", "六", "七", "八", "九");
			if ("十".equals(value)) return 10;
			if (value.startsWith("十") && value.length() == 2) {
				int digit = digits.indexOf(value.substring(1)) + 1;
				return digit > 0 ? 10 + digit : null;
			}
			if ("二十".equals(value)) return 20;
			int result = digits.indexOf(value) + 1;
			return result < 1 ? null : result;
		}
	}
}
