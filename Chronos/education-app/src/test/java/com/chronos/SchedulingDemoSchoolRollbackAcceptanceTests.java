package com.chronos;

import static org.assertj.core.api.Assertions.assertThat;

import com.chronos.education.scheduling.dao.ScheduleEntryRepository;
import com.chronos.education.scheduling.model.ScheduleEntry;
import com.chronos.education.scheduling.model.SchedulePlanVersionView;
import com.chronos.education.scheduling.service.SchedulePlanVersionService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Persistent rollback acceptance; keeps the complete version chain in the retained demo copy. */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CHRONOS_DEMO_SCHEDULE_ROLLBACK", matches = "true")
@EnabledIfEnvironmentVariable(
		named = "CHRONOS_DB_URL",
		matches = "jdbc:postgresql:.*/chronos_schedule_verify_[^/?]+(\\?.*)?")
class SchedulingDemoSchoolRollbackAcceptanceTests {
	private static final String TERM = "2026-2027-1";
	private static final String REFERENCED_NEW_ENTRY = "efa2248e-49ad-49e0-9eea-0ff12309dc9f";

	@Autowired private SchedulePlanVersionService versions;
	@Autowired private ScheduleEntryRepository entries;
	@Autowired private JdbcTemplate jdbc;

	@Test
	void olderVersionRestoresEffectiveTimetableAndKeepsReferencedHistory() {
		List<SchedulePlanVersionView> before = versions.versions(TERM);
		assertThat(before.getFirst().versionNo()).isIn(3, 4);
		SchedulePlanVersionView versionTwo = before.stream()
				.filter(value -> value.versionNo() == 2).findFirst().orElseThrow();
		SchedulePlanVersionView versionThree = before.stream()
				.filter(value -> value.versionNo() == 3).findFirst().orElseThrow();

		if (before.getFirst().versionNo() == 3) {
			var rollback = versions.rollback(versionTwo.id(), "admin");
			assertThat(rollback.getVersionNo()).isEqualTo(4);
			assertThat(rollback.getSourceVersionNo()).isEqualTo(2);
		}
		assertThat(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(TERM))
				.hasSize(21)
				.filteredOn(entry -> !"CANCELLED".equals(entry.getStatus()))
				.hasSize(19);
		ScheduleEntry retained = entries.findById(REFERENCED_NEW_ENTRY).orElseThrow();
		assertThat(retained.getStatus()).isEqualTo("CANCELLED");
		assertThat(retained.getDayOfWeek()).isEqualTo(1);
		assertThat(retained.getPeriodNo()).isEqualTo(1);
		assertReferencesStillPointToEntries();

		var restored = versions.rollback(versionThree.id(), "admin");
		assertThat(restored.getVersionNo()).isEqualTo(5);
		assertThat(restored.getSourceVersionNo()).isEqualTo(3);
		assertThat(entries.findBySemesterCodeOrderByDayOfWeekAscPeriodNoAsc(TERM))
				.hasSize(54)
				.filteredOn(entry -> "SCHEDULED".equals(entry.getStatus()))
				.hasSize(53);
		assertThat(entries.findById(REFERENCED_NEW_ENTRY).orElseThrow().getStatus())
				.isEqualTo("SCHEDULED");
		assertReferencesStillPointToEntries();
	}

	private void assertReferencesStillPointToEntries() {
		Integer missing = jdbc.queryForObject("""
				select (select count(*) from edu_exam_course_suspension_item item
				        left join edu_schedule_entry entry on entry.id = item.source_entry_id
				        where item.source_entry_id is not null and entry.id is null)
				     + (select count(*) from edu_schedule_date_exception item
				        left join edu_schedule_entry entry on entry.id = item.source_entry_id
				        where item.source_entry_id is not null and entry.id is null)
				""", Integer.class);
		assertThat(missing).isZero();
	}
}
