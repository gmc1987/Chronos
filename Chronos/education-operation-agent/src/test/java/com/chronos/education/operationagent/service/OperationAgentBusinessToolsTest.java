package com.chronos.education.operationagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.education.scheduling.service.OfficialHolidayImportService;
import com.chronos.education.scheduling.service.OfficialHolidayImportService.ImportResult;
import com.chronos.security.DirectoryDataScopeGuard;

@ExtendWith(MockitoExtension.class)
class OperationAgentBusinessToolsTest {
	@Mock JdbcTemplate jdbc;
	@Mock AcademicTermRepository terms;
	@Mock OfficialHolidayImportService holidays;
	@Mock DirectoryDataScopeGuard dataScopes;
	@InjectMocks OperationAgentBusinessTools tools;

	@BeforeEach
	void existingTerm() {
		when(jdbc.queryForList(
				"select id from t_organization where status=1 and (id=? or org_code=? or organization_name=?)",
				String.class, "school", "school", "school")).thenReturn(List.of("school"));
		var term = new AcademicTerm();
		term.setOrganizationId("school");
		term.setStartDate(LocalDate.of(2026, 9, 1));
		term.setEndDate(LocalDate.of(2027, 1, 30));
		when(terms.findById("term")).thenReturn(Optional.of(term));
	}

	@Test
	void calendarPreviewUsesStoredDatesWithoutDatesInThePlan() {
		var result = tools.execute("academic.calendar.prepare",
				Map.of("organizationId", "school", "termId", "term", "dryRun", true), null, "", "admin");

		assertThat(result).containsEntry("years", List.of(2026, 2027));
		verifyNoInteractions(holidays);
	}

	@Test
	void calendarImportUsesStoredDatesEvenIfThePlanContainsOtherDates() {
		when(holidays.importOfficial("term", 2026)).thenReturn(new ImportResult(2026, "", Map.of()));
		when(holidays.importOfficial("term", 2027)).thenReturn(new ImportResult(2027, "", Map.of()));

		var result = tools.execute("academic.calendar.prepare",
				Map.of("organizationId", "school", "termId", "term", "startDate", "2020-01-01",
						"endDate", "2020-12-31"), null, "", "admin");

		verify(holidays).importOfficial("term", 2026);
		verify(holidays).importOfficial("term", 2027);
		assertThat(result).containsEntry("warnings", List.of());
	}

	@Test
	void calendarImportFailsWhenNoYearWasSynchronized() {
		when(holidays.importOfficial("term", 2026)).thenThrow(new IllegalStateException("连接超时"));
		when(holidays.importOfficial("term", 2027)).thenThrow(new IllegalStateException("HTTP 404"));

		assertThatThrownBy(() -> tools.execute("academic.calendar.prepare",
				Map.of("organizationId", "school", "termId", "term"), null, "", "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("所有目标年度均未导入", "2026", "连接超时", "2027", "HTTP 404");
	}
}
