package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
		name = "wf_instance",
		indexes = @Index(name = "idx_wf_instance_engine_instance", columnList = "engine_instance_id")
)
@Getter
@Setter
public class WorkflowInstance extends BaseEntity {
	@Column(name = "definition_id", nullable = false, length = 64)
	private String definitionId;

	@Column(name = "definition_version", nullable = false, length = 40)
	private String definitionVersion;

	@Column(name = "business_key", length = 160)
	private String businessKey;

	@Column(nullable = false, length = 128)
	private String initiator;

	@Column(nullable = false, length = 30)
	private String status = "RUNNING";

	@Column(name = "current_node_key", length = 100)
	private String currentNodeKey;

	@Lob
	@Column(name = "variables_json")
	private String variablesJson;

	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	/**
	 * LEGACY 仅用于兼容改造前已经创建的实例；新实例必须写入 FLOWABLE。
	 * 该字段是两套运行时之间的硬隔离边界，业务代码不得对 FLOWABLE 实例调用 advance()。
	 */
	@Column(
			name = "engine_type",
			nullable = false,
			length = 20,
			columnDefinition = "varchar(20) default 'LEGACY'"
	)
	private String engineType = "LEGACY";

	/** Flowable 的流程实例 ID。Chronos 实例 ID 仍作为门户和业务侧稳定主键。 */
	@Column(name = "engine_instance_id", length = 64, unique = true)
	private String engineInstanceId;

	/** 多人会签完成时锁定流程实例，确保节点只推进一次。 */
	@Version
	@Column(name = "lock_version", nullable = false, columnDefinition = "bigint default 0")
	private Long lockVersion = 0L;
}
