package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chronos.service.iService.IAuditLogService;

class AccommodationServiceTest {
	private JdbcTemplate jdbc;
	private IAuditLogService audit;
	private AccommodationService service;

	@BeforeEach
	void setUp() {
		jdbc = mock(JdbcTemplate.class);
		audit = mock(IAuditLogService.class);
		service = new AccommodationService(jdbc, audit);
	}

	@Test
	void cancelIsIdempotentWhenAssignmentAlreadyEnded() {
		when(jdbc.update(
				"update edu_student_accommodation set status='CANCELLED' where id=? and status='ACTIVE'",
				"assignment-1")).thenReturn(0);
		when(jdbc.queryForObject(
				"select count(*) from edu_student_accommodation where id=?",
				Long.class,
				"assignment-1")).thenReturn(1L);

		assertThatCode(() -> service.cancelAssignment("assignment-1", "admin"))
				.doesNotThrowAnyException();
		verifyNoInteractions(audit);
	}

	@Test
	void cancelActiveAssignmentChangesStatusAndWritesAudit() {
		when(jdbc.update(
				"update edu_student_accommodation set status='CANCELLED' where id=? and status='ACTIVE'",
				"assignment-1")).thenReturn(1);

		service.cancelAssignment("assignment-1", "admin");

		verify(audit).log("ACCOMMODATION_ASSIGN_CANCEL", "STUDENT_ACCOMMODATION", "assignment-1");
	}

	@Test
	void cancelRejectsUnknownAssignment() {
		when(jdbc.update(
				"update edu_student_accommodation set status='CANCELLED' where id=? and status='ACTIVE'",
				"missing")).thenReturn(0);
		when(jdbc.queryForObject(
				"select count(*) from edu_student_accommodation where id=?",
				Long.class,
				"missing")).thenReturn(0L);

		assertThatThrownBy(() -> service.cancelAssignment("missing", "admin"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("住宿分配不存在");
	}
}
