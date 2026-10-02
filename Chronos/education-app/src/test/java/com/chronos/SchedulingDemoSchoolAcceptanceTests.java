package com.chronos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chronos.education.scheduling.dao.CourseOfferingRepository;
import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.AutoScheduleCommand;
import com.chronos.education.scheduling.model.ScheduleCandidateView;
import com.chronos.education.scheduling.service.AutoSchedulingService;
import com.chronos.education.scheduling.service.ScheduleGenerationJobService;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;
import com.chronos.education.scheduling.service.ScheduleQualityAnalysisService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Persistent business acceptance against a retained copy of the demo school.
 * The source database must never be used: this test applies and publishes a timetable.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CHRONOS_DEMO_SCHEDULE_ACCEPTANCE", matches = "true")
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_DB_URL",
		matches = "jdbc:postgresql:.*/chronos_schedule_verify_[^/?]+(\\?.*)?")
class SchedulingDemoSchoolAcceptanceTests {
	private static final String TERM = "2026-2027-1";
	private static final String NAME = "2026-10-02 演示学校全量排课验收-终版";

	@Autowired private CourseOfferingRepository offerings;
	@Autowired private ScheduleEntryRepository entries;
	@Autowired private ScheduleGenerationJobService jobs;
	@Autowired private AutoSchedulingService scheduling;
	@Autowired private ScheduleQualityAnalysisService quality;
	@Autowired private SchedulePlanVersionService versions;
	@Autowired private ObjectMapper json;

	@Test
	void fullDemoTermCanBeGeneratedReviewedAppliedAndPublished() throws Exception {
		assertThat(offerings.findBySemesterCodeOrderByOfferingCode(TERM))
				.as("retained demo school must have all teaching tasks")
				.hasSize(17);
		assertThat(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(TERM))
					.as("retained demo school must have its original timetable")
					.hasSize(21);
		assertThat(scheduling.list(TERM).stream()
					.filter(candidate -> candidate.planName().startsWith(NAME)))
					.as("do not run this persistent acceptance twice on the same copy")
					.isEmpty();

		var command = new AutoScheduleCommand(TERM, NAME, "FULL", Set.of(),
				2, 5, 8, 1, 20);
		var job = jobs.submit(command, "admin");
		Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
		var current = jobs.require(job.getId());
		while (Set.of("QUEUED", "RUNNING").contains(current.getStatus())
				&& Instant.now().isBefore(deadline)) {
			Thread.sleep(250);
			current = jobs.require(job.getId());
		}
		assertThat(current.getStatus())
				.describedAs("job %s failed: %s", job.getId(), current.getErrorMessage())
				.isEqualTo("SUCCEEDED");
		List<String> ids = json.readValue(current.getResultCandidateIds(),
				new TypeReference<List<String>>() { });
		assertThat(ids).hasSize(2);
		List<ScheduleCandidateView> candidates = ids.stream().map(scheduling::get).toList();
		assertThat(candidates).allSatisfy(candidate -> {
			assertThat(candidate.generationMode()).isEqualTo("FULL");
			assertThat(candidate.unscheduledLessons()).isZero();
			assertThat(candidate.metrics().scheduledLessons()).isPositive();
			assertThat(candidate.reviewStatus()).isEqualTo("DRAFT");
		});
		assertThat(scheduling.compare(ids)).hasSize(2);
		assertThat(scheduling.preview(ids.getFirst())).isNotNull();
		assertThat(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(TERM))
					.as("candidate generation must leave the current timetable unchanged")
					.hasSize(21);

		for (String id : ids) {
			assertThat(scheduling.submitReview(id, "admin").reviewStatus())
					.isEqualTo("SUBMITTED");
			assertThatThrownBy(() -> scheduling.review(id, true, "演示学校验收", "admin"))
					.hasMessageContaining("不能审核自己的方案");
			assertThat(scheduling.review(id, true, "演示学校验收", "academic.demo")
					.reviewStatus()).isEqualTo("APPROVED");
		}
		assertThat(scheduling.apply(ids.getFirst(), "admin").status()).isEqualTo("APPLIED");
		assertThatThrownBy(() -> scheduling.apply(ids.getLast(), "admin"))
				.hasMessageContaining("已变化");
		assertThat(quality.publishBlockers(TERM)).isEmpty();
		var version = versions.publish(TERM, "admin");
		assertThat(version.getEntryCount()).isEqualTo(
				entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(TERM).size());
		assertThat(versions.latestPublishedEntries(TERM)).hasSize(version.getEntryCount());
	}
}
