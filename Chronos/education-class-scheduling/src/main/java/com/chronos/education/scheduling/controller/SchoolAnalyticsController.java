package com.chronos.education.scheduling.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.service.SchoolAnalyticsService;
import com.chronos.education.scheduling.service.SchoolAnalyticsService.DailyRollup;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/education/data-center/school-rollups")
@RequiredArgsConstructor
public class SchoolAnalyticsController {
	private static final String VIEW = "@iamAuthorization.has(authentication,'education:data-center:view')";
	private final SchoolAnalyticsService analytics;

	@GetMapping
	@PreAuthorize(VIEW)
	public ResultData<List<DailyRollup>> list(@RequestParam String schoolId, @RequestParam LocalDate start,
			@RequestParam LocalDate end, Authentication actor) {
		return ok(analytics.dailyRollups(schoolId, start, end, actor));
	}

	@PostMapping
	@PreAuthorize("@iamAuthorization.has(authentication,'education:data-center:import')")
	public ResultData<Integer> save(@RequestParam String schoolId, @RequestBody @Valid ImportCommand command,
			Authentication actor) {
		return ok(analytics.importDailyRollups(schoolId, command.rows(), actor));
	}

	@GetMapping("/export")
	@PreAuthorize(VIEW)
	public ResponseEntity<byte[]> export(@RequestParam String schoolId, @RequestParam LocalDate start,
			@RequestParam LocalDate end, Authentication actor) {
		var csv = new StringBuilder("\uFEFF日期,在籍学生,在岗教师,开设班级\r\n");
		for (var row : analytics.dailyRollups(schoolId, start, end, actor)) {
			csv.append(row.date()).append(',').append(row.studentCount()).append(',')
					.append(row.teacherCount()).append(',').append(row.classCount()).append("\r\n");
		}
		return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=school-daily-metrics.csv")
				.header(HttpHeaders.CACHE_CONTROL, "no-store").body(csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	public record ImportCommand(@NotNull @Size(min = 1, max = 366) List<@NotNull @Valid DailyRollup> rows) {}

	private static <T> ResultData<T> ok(T data) {
		return ResultData.<T>builder().code("200").msg("success").data(data).build();
	}
}
