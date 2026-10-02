package com.chronos.education.scheduling.service;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 读取附国务院公告链接的年度数据；网络或公告缺失时保留已配置校历。 */
@Service
public class OfficialHolidayImportService {
	private static final Logger log = LoggerFactory.getLogger(OfficialHolidayImportService.class);
	private static final String DATA_URL =
			"https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json";
	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5)).build();
	private final ObjectMapper mapper;
	private final AcademicTermRepository terms;
	private final AcademicCalendarService calendar;

	public OfficialHolidayImportService(
			ObjectMapper mapper,
			AcademicTermRepository terms,
			AcademicCalendarService calendar) {
		this.mapper = mapper;
		this.terms = terms;
		this.calendar = calendar;
	}

	public record HolidayDate(LocalDate date, String name, boolean offDay) { }

	public record ImportResult(int year, String sourceUrl, Map<String, Integer> counts) { }

	public ImportResult importOfficial(String termId, int year) {
		assertYear(year);
		String url = DATA_URL.formatted(year);
		String body;
		try {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(10)).GET().build();
			HttpResponse<String> response = client.send(request,
					HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("年度节假日数据获取失败：HTTP " + response.statusCode());
			}
			body = response.body();
		} catch (IOException | InterruptedException exception) {
			if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
			throw new IllegalStateException("年度节假日数据获取失败", exception);
		}
		Parsed parsed = parse(body, year, true);
		return new ImportResult(year, parsed.sourceUrl(),
				calendar.importHolidays(termId, parsed.days(), "AUTO", parsed.sourceUrl()));
	}

	public ImportResult importManual(String termId, int year, String content) {
		assertYear(year);
		Parsed parsed = parse(content, year, false);
		return new ImportResult(year, parsed.sourceUrl(),
				calendar.importHolidays(termId, parsed.days(), "MANUAL", parsed.sourceUrl()));
	}

	/** 启动 30 秒后执行，此后每日同步已公布的当前及下一年度安排。 */
	@Scheduled(initialDelay = 30000, fixedDelay = 86400000)
	public void automaticSync() {
		int currentYear = LocalDate.now().getYear();
		for (AcademicTerm term : terms.findAll()) {
			for (int year = currentYear; year <= currentYear + 1; year++) {
				if (term.getStartDate().getYear() > year
						|| term.getEndDate().getYear() < year) continue;
				try {
					importOfficial(term.getId(), year);
				} catch (RuntimeException exception) {
					log.warn("节假日自动同步跳过 term={}, year={}: {}",
							term.getTermCode(), year, exception.getMessage());
				}
			}
		}
	}

	private record Parsed(List<HolidayDate> days, String sourceUrl) { }

	private Parsed parse(String content, int expectedYear, boolean official) {
		if (content == null || content.isBlank() || content.length() > 200000) {
			throw new IllegalArgumentException("节假日 JSON 为空或超过大小限制");
		}
		try {
			JsonNode root = mapper.readTree(content);
			if (root.path("year").asInt(-1) != expectedYear
					|| !root.path("days").isArray()) {
				throw new IllegalArgumentException("节假日 JSON 年份或日期格式不正确");
			}
			JsonNode papers = root.path("papers");
			String sourceUrl = papers.isArray() && !papers.isEmpty()
					? papers.get(0).asText() : null;
			if (sourceUrl != null && !sourceUrl.isBlank()) {
				URI source = URI.create(sourceUrl);
				if (!"https".equals(source.getScheme())
						|| source.getHost() == null || sourceUrl.length() > 500) {
					throw new IllegalArgumentException("公告链接必须是有效的 HTTPS 地址");
				}
			}
			if (official && (sourceUrl == null
					|| !"www.gov.cn".equals(URI.create(sourceUrl).getHost()))) {
				throw new IllegalStateException("该年度尚无可核验的国务院节假日公告");
			}
			List<HolidayDate> days = new ArrayList<>();
			Set<LocalDate> uniqueDates = new HashSet<>();
			for (JsonNode item : root.path("days")) {
				LocalDate date = LocalDate.parse(item.path("date").asText());
				String name = item.path("name").asText().trim();
				if (date.getYear() != expectedYear || name.isEmpty()
						|| name.length() > 128 || !item.path("isOffDay").isBoolean()
						|| !uniqueDates.add(date)) {
					throw new IllegalArgumentException("节假日 JSON 存在无效或重复日期");
				}
				days.add(new HolidayDate(date, name, item.path("isOffDay").booleanValue()));
			}
			if (days.isEmpty()) {
				throw new IllegalStateException("该年度尚未公布节假日安排");
			}
			return new Parsed(days, sourceUrl);
		} catch (IOException | java.time.format.DateTimeParseException exception) {
			throw new IllegalArgumentException("节假日 JSON 解析失败", exception);
		}
	}

	private void assertYear(int year) {
		if (year < 2000 || year > LocalDate.now().getYear() + 2) {
			throw new IllegalArgumentException("导入年份不在允许范围内");
		}
	}
}
