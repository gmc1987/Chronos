package com.chronos.education.operationagent.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_operation_agent_plan")
public class OperationAgentPlan extends BaseEntity {
	@Column(name = "plan_name", nullable = false, length = 128) private String planName;
	@Column(name = "agent_code", nullable = false, length = 64) private String agentCode = "SEMESTER_OPERATION_V1";
	@Column(name = "objective", nullable = false, length = 1000) private String objective;
	@Column(name = "rule_text", nullable = false, columnDefinition = "text") private String ruleText;
	@Column(name = "cycle_type", nullable = false, length = 24) private String cycleType = "MANUAL";
	@Column(name = "cron_expression", length = 128) private String cronExpression;
	@Column(name = "period_start") private LocalDate periodStart;
	@Column(name = "period_end") private LocalDate periodEnd;
	@Column(name = "knowledge_base_id", length = 64) private String knowledgeBaseId;
	@Column(name = "authorized_tools_json", nullable = false, columnDefinition = "text") private String authorizedToolsJson = "[]";
	@Column(name = "owner_username", nullable = false, length = 128) private String ownerUsername;
	@Column(name = "visibility", nullable = false, length = 16) private String visibility = "OWNER";
	@Column(name = "enabled", nullable = false) private Boolean enabled = true;
	@Column(name = "next_scheduled_at") private LocalDateTime nextScheduledAt;
	@Column(name = "last_scheduled_at") private LocalDateTime lastScheduledAt;
	@Column(name = "plan_version", nullable = false) private Integer planVersion = 1;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
