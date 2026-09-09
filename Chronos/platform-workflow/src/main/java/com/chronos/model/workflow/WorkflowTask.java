package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
		name = "wf_task",
		indexes = @Index(name = "idx_wf_task_engine_task", columnList = "engine_task_id")
)
@Getter
@Setter
public class WorkflowTask extends BaseEntity {
	@Column(name = "instance_id", nullable = false, length = 64)
	private String instanceId;

	@Column(name = "node_key", nullable = false, length = 100)
	private String nodeKey;

	@Column(name = "node_name", nullable = false, length = 160)
	private String nodeName;

	/** 未认领的候选任务允许为空；认领后才写入实际处理人。 */
	@Column(name = "assignee", length = 128)
	private String assignee;

	@Column(nullable = false, length = 30)
	private String status = "PENDING";

	@Column(length = 1000)
	private String comment;

	@Column(name = "completed_at")
	private LocalDateTime completedAt;

	@Column(name = "due_at")
	private LocalDateTime dueAt;

	@Column(name = "reminded_at")
	private LocalDateTime remindedAt;

	/** SLA 状态：NORMAL、DUE_SOON、OVERDUE、ESCALATED。 */
	@Column(name = "sla_status", nullable = false, length = 30, columnDefinition = "varchar(30) default 'NORMAL'")
	private String slaStatus = "NORMAL";

	@Column(name = "reminder_count", nullable = false, columnDefinition = "integer default 0")
	private Integer reminderCount = 0;

	@Column(name = "escalation_level", nullable = false, columnDefinition = "integer default 0")
	private Integer escalationLevel = 0;

	@Column(name = "next_reminder_at")
	private LocalDateTime nextReminderAt;

	/** Flowable 任务 ID。该表是门户查询投影，Flowable 才是 FLOWABLE 实例的运行时事实来源。 */
	@Column(name = "engine_task_id", length = 64, unique = true)
	private String engineTaskId;

	/** 同一审批节点每次进入都会生成独立轮次，避免退回后重新审批时混入旧任务统计。 */
	@Column(name = "round_key", length = 64)
	private String roundKey;

	/** NORMAL 为普通审批；STARTER_REWORK 表示退回发起人修改后重新提交。 */
	@Column(
			name = "task_kind",
			nullable = false,
			length = 32,
			columnDefinition = "varchar(32) default 'NORMAL'"
	)
	private String taskKind = "NORMAL";

	/** 发起人修改任务完成后，需要重新创建审批任务的原节点。 */
	@Column(name = "resume_node_key", length = 100)
	private String resumeNodeKey;

	/** 乐观锁用于拦截同一任务被重复提交。实例级悲观锁负责串行化同节点多人审批。 */
	@Version
	@Column(name = "lock_version", nullable = false, columnDefinition = "bigint default 0")
	private Long lockVersion = 0L;
}
