package com.chronos.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.chronos.Idao.IAuditLogRepository;
import com.chronos.model.pojo.AuditLog;
import com.chronos.service.impl.AuditLogQueryService.AuditLogQuery;

class AuditLogExportServiceTest {
	@Test
	void escapesFormulaAndCsvCharacters() {
		IAuditLogRepository repository = mock(IAuditLogRepository.class);
		AuditLogQueryService queryService = mock(AuditLogQueryService.class);
		@SuppressWarnings("unchecked")
		Specification<AuditLog> specification = mock(Specification.class);
		AuditLog auditLog = new AuditLog();
		auditLog.setCreateTime(LocalDateTime.of(2026, 9, 10, 9, 0));
		auditLog.setUsername("=HYPERLINK(\"https://invalid\")");
		auditLog.setAction("EDUCATION_TEST");
		auditLog.setDetail("字段,包含逗号和\"引号\"");
		when(queryService.specification(any())).thenReturn(specification);
		when(repository.findAll(
				org.mockito.ArgumentMatchers.<Specification<AuditLog>>any(),
				any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(auditLog)));
		AuditLogExportService service = new AuditLogExportService(repository, queryService);

		byte[] exported = service.export(new AuditLogQuery(
				"EDUCATION",
				null,
				null,
				null,
				null,
				null));

		String csv = new String(exported, StandardCharsets.UTF_8);
		assertThat(csv).startsWith("\uFEFF发生时间,操作人,动作编码,操作详情");
		assertThat(csv).contains("\"'=HYPERLINK(\"\"https://invalid\"\")\"");
		assertThat(csv).contains("\"字段,包含逗号和\"\"引号\"\"\"");
	}
}
