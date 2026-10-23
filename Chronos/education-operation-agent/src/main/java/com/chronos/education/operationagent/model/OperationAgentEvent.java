package com.chronos.education.operationagent.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_operation_agent_event", indexes = @Index(
		name = "idx_edu_operation_event_run_seq", columnList = "run_id,event_sequence"))
public class OperationAgentEvent extends BaseEntity {
	@Column(name = "run_id", nullable = false, length = 64) private String runId;
	@Column(name = "event_sequence", nullable = false) private Long eventSequence;
	@Column(name = "event_type", nullable = false, length = 48) private String eventType;
	@Column(name = "level", nullable = false, length = 16) private String level = "INFO";
	@Column(name = "title", nullable = false, length = 200) private String title;
	@Column(name = "detail", columnDefinition = "text") private String detail;
	@Column(name = "metadata_json", columnDefinition = "text") private String metadataJson;
}
