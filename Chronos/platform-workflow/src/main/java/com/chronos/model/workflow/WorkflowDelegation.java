package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "wf_delegation", indexes = @Index(name = "idx_wf_delegation_active",
		columnList = "delegator,enabled,start_at,end_at"))
@Getter
@Setter
public class WorkflowDelegation extends BaseEntity {
	@Column(nullable = false, length = 128)
	private String delegator;

	@Column(nullable = false, length = 128)
	private String delegatee;

	@Column(name = "definition_id", length = 64)
	private String definitionId;

	@Column(name = "start_at", nullable = false)
	private LocalDateTime startAt;

	@Column(name = "end_at", nullable = false)
	private LocalDateTime endAt;

	@Column(nullable = false)
	private Boolean enabled = true;

	@Column(length = 500)
	private String reason;
}
