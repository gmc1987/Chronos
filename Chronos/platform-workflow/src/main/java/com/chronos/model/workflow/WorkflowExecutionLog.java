package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 自动节点每次实际调用的审计记录，Flowable 重试会产生新的记录。 */
@Entity
@Table(
		name = "wf_execution_log",
		indexes = {
				@Index(name = "idx_wf_execution_instance", columnList = "instance_id,create_time"),
				@Index(name = "idx_wf_execution_node", columnList = "node_id,create_time")
		}
)
@Getter
@Setter
public class WorkflowExecutionLog extends BaseEntity {
	@Column(name = "instance_id", nullable = false, length = 64)
	private String instanceId;

	@Column(name = "engine_instance_id", length = 64)
	private String engineInstanceId;

	@Column(name = "node_id", nullable = false, length = 64)
	private String nodeId;

	@Column(name = "node_key", nullable = false, length = 100)
	private String nodeKey;

	@Column(nullable = false, length = 80)
	private String executor;

	@Column(nullable = false, length = 20)
	private String status;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "request_json", columnDefinition = "text")
	private String requestJson;

	@Column(name = "response_json", columnDefinition = "text")
	private String responseJson;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;
}
