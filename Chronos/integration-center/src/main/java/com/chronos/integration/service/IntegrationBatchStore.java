package com.chronos.integration.service;

import com.chronos.integration.dao.SyncCursorRepository;
import com.chronos.integration.dao.SyncRunRepository;
import com.chronos.integration.model.SyncCursor;
import com.chronos.integration.model.SyncRun;
import com.chronos.integration.service.IntegrationExtensionService.DiffEntry;
import com.chronos.service.iService.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IntegrationBatchStore {
	private final JdbcTemplate jdbc;
	private final SyncRunRepository runs;
	private final SyncCursorRepository cursors;
	private final ObjectMapper json;
	private final IAuditLogService audit;

	@Transactional
	public SyncRun commit(SyncRun run, List<DiffEntry> records, String nextCursor, String actor) {
		SyncRun saved = runs.saveAndFlush(run);
		var parameters = new ArrayList<Object[]>(records.size());
		for (int index = 0; index < records.size(); index++) {
			var item = records.get(index);
			parameters.add(new Object[] { saved.getId(), index + 1, item.sourceKey(), json.valueToTree(item.fields()).toString() });
		}
		if (!parameters.isEmpty()) {
			jdbc.batchUpdate("""
					INSERT INTO int_inbound_record (run_id,item_no,source_key,mapped_json)
					VALUES (?,?,?,CAST(? AS jsonb))
					""", parameters);
		}
		if (nextCursor != null && !nextCursor.isBlank()) {
			var cursor = cursors.findByJobId(saved.getJobId()).orElseGet(SyncCursor::new);
			cursor.setJobId(saved.getJobId());
			cursor.setCursorValue(nextCursor);
			cursor.setLastSuccessAt(LocalDateTime.now());
			cursors.save(cursor);
		}
		audit.log(actor, "INTEGRATION_BATCH_ACCEPTED", "runId=" + saved.getId() + ", count=" + records.size());
		return saved;
	}

	@Transactional(readOnly = true)
	public List<InboundRecord> records(String runId, int page) {
		if (!runs.existsById(runId)) throw new IllegalArgumentException("运行记录不存在");
		return jdbc.query("""
				SELECT item_no,source_key,mapped_json::text AS mapped_json
				FROM int_inbound_record WHERE run_id=? ORDER BY item_no LIMIT 50 OFFSET ?
				""", (row, index) -> new InboundRecord(row.getInt("item_no"), row.getString("source_key"),
					row.getString("mapped_json")), runId, Math.max(0, page) * 50L);
	}

	public record InboundRecord(int itemNo, String sourceKey, String mappedJson) {}
}
