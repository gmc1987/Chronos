package com.chronos.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.chronos.model.pojo.AuditLog;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.impl.AuditLogExportService;
import com.chronos.service.impl.AuditLogQueryService;

class AuditLogControllerTest {
	@Test
	void returnsStablePageContract() {
		AuditLogQueryService service = mock(AuditLogQueryService.class);
		AuditLog auditLog = new AuditLog();
		auditLog.setAction("EDUCATION_AUTO_SCHEDULE_GENERATE");
		when(service.query(any(), anyInt(), anyInt()))
				.thenReturn(new PageImpl<>(
						List.of(auditLog),
						PageRequest.of(0, 20),
						1));
		AuditLogController controller = controller(service);

		var result = controller.query(
				"EDUCATION",
				null,
				null,
				null,
				null,
				null,
				0,
				20);

		assertThat(result.getData().totalElements()).isEqualTo(1);
		assertThat(result.getData().content())
				.extracting(AuditLog::getAction)
				.containsExactly("EDUCATION_AUTO_SCHEDULE_GENERATE");
		verify(service).query(any(), anyInt(), anyInt());
	}

	@Test
	void rejectsReverseTimeRange() {
		AuditLogController controller = controller(mock(AuditLogQueryService.class));
		LocalDateTime start = LocalDateTime.of(2026, 9, 10, 12, 0);
		LocalDateTime end = start.minusHours(1);

		assertThatThrownBy(() -> controller.query(
				"ALL",
				null,
				null,
				null,
				start,
				end,
				0,
				20))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("开始时间不能晚于结束时间");
	}

	private AuditLogController controller(AuditLogQueryService service) {
		return new AuditLogController(
				service,
				mock(AuditLogExportService.class),
				mock(IAuditLogService.class));
	}
}
