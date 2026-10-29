package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chronos.education.scheduling.dao.AcademicTermRepository;
import com.chronos.education.scheduling.model.AcademicTerm;
import com.chronos.service.iService.IAuditLogService;

class SemesterArchiveServiceTest {
	private AcademicTermRepository terms;
	private JdbcTemplate jdbc;
	private IAuditLogService audit;
	private SemesterArchiveService service;
	private AcademicTerm term;

	@BeforeEach
	void setUp() {
		terms = mock(AcademicTermRepository.class);
		jdbc = mock(JdbcTemplate.class);
		audit = mock(IAuditLogService.class);
		service = new SemesterArchiveService(terms, jdbc, audit);
		term = new AcademicTerm();
		term.setId("term-1");
		term.setTermCode("2025-2026-2");
		term.setStatus("ACTIVE");
		term.setCurrentTerm(false);
		term.setEndDate(LocalDate.now().minusDays(1));
		when(terms.findById("term-1")).thenReturn(Optional.of(term));
	}

	@Test
	void archivesOnlyAfterAllReadinessChecksPass() {
		readyCounts();
		when(terms.save(any(AcademicTerm.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AcademicTerm archived = service.archive("term-1", "admin");

		assertThat(archived.getStatus()).isEqualTo("ARCHIVED");
		assertThat(archived.getCurrentTerm()).isFalse();
		verify(audit).log("admin", "EDUCATION_SEMESTER_ARCHIVE", "semester=2025-2026-2");
	}

	@Test
	void rejectsCurrentTermEvenWhenBusinessDataIsReady() {
		term.setCurrentTerm(true);
		readyCounts();

		assertThatThrownBy(() -> service.archive("term-1", "admin"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("当前启用学期不能归档，请先启用下一学期");
	}

	private void readyCounts() {
		when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
				.thenReturn(2L, 1L, 1L, 1L, 1L, 0L, 0L);
	}
}
