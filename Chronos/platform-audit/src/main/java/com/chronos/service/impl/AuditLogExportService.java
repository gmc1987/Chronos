package com.chronos.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAuditLogRepository;
import com.chronos.model.pojo.AuditLog;
import com.chronos.service.impl.AuditLogQueryService.AuditLogQuery;

/** 生成可由通用办公软件打开的 UTF-8 CSV 审计报表。 */
@Service
@Transactional(readOnly = true)
public class AuditLogExportService {
	private static final int MAX_EXPORT_ROWS = 10_000;
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter
			.ofPattern("yyyy-MM-dd HH:mm:ss");
	private final IAuditLogRepository repository;
	private final AuditLogQueryService queryService;

	public AuditLogExportService(
			IAuditLogRepository repository,
			AuditLogQueryService queryService) {
		this.repository = repository;
		this.queryService = queryService;
	}

	public byte[] export(AuditLogQuery query) {
		List<AuditLog> rows = repository.findAll(
				queryService.specification(query),
				PageRequest.of(
						0,
						MAX_EXPORT_ROWS + 1,
						Sort.by(Sort.Direction.DESC, "createTime")))
				.getContent();
		if (rows.size() > MAX_EXPORT_ROWS) {
			throw new IllegalStateException("审计日志超过 10000 条，请缩小时间范围后导出");
		}
		StringBuilder csv = new StringBuilder("\uFEFF发生时间,操作人,动作编码,操作详情\r\n");
		for (AuditLog row : rows) {
			csv.append(cell(row.getCreateTime() == null
					? ""
					: DATE_TIME_FORMATTER.format(row.getCreateTime())))
					.append(',')
					.append(cell(row.getUsername()))
					.append(',')
					.append(cell(row.getAction()))
					.append(',')
					.append(cell(row.getDetail()))
					.append("\r\n");
		}
		return csv.toString().getBytes(StandardCharsets.UTF_8);
	}

	/** 防止以 =、+、-、@ 等开头的日志内容被表格软件作为公式执行。 */
	private String cell(String value) {
		String sanitized = value == null ? "" : value;
		if (!sanitized.isEmpty()
				&& "=+-@\t\r".indexOf(sanitized.charAt(0)) >= 0) {
			sanitized = "'" + sanitized;
		}
		return "\"" + sanitized.replace("\"", "\"\"") + "\"";
	}
}
