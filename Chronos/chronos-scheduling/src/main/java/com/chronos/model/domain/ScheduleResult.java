package com.chronos.model.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.chronos.model.domain.enums.ScheduleStatus;
import com.chronos.model.pojo.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 排程结果聚合根。
 * <p>
 * 表示一次排程运行的整体结果，包括时间窗、运行状态、评分、快照与明细分配记录。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_schedule_result", comment = "领域-排程结果")
public class ScheduleResult extends BaseEntity {

	/** 所属组织。 */
	@Column(name = "organization_id", nullable = false, length = 64)
	private String organizationId;

	/** 排程名称。 */
	@Column(name = "schedule_name", length = 120, nullable = false)
	private String scheduleName;

	/** 排程编码。 */
	@Column(name = "schedule_code", length = 100, nullable = false)
	private String scheduleCode;

	/** 场景标识（如门诊排班、班级排课）。 */
	@Column(name = "scenario", length = 120)
	private String scenario;

	/** 排程状态。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "schedule_status", nullable = false, length = 16)
	private ScheduleStatus scheduleStatus = ScheduleStatus.CREATED;

	/** 计划窗口开始时间。 */
	@Column(name = "planning_start_time", nullable = false)
	private LocalDateTime planningStartTime;

	/** 计划窗口结束时间。 */
	@Column(name = "planning_end_time", nullable = false)
	private LocalDateTime planningEndTime;

	/** 实际运行开始时间。 */
	@Column(name = "started_at")
	private LocalDateTime startedAt;

	/** 实际运行结束时间。 */
	@Column(name = "finished_at")
	private LocalDateTime finishedAt;

	/** 硬约束得分（通常越高越好，或违规越少越好）。 */
	@Column(name = "hard_score", precision = 20, scale = 4)
	private BigDecimal hardScore;

	/** 软约束得分。 */
	@Column(name = "soft_score", precision = 20, scale = 4)
	private BigDecimal softScore;

	/** 总目标得分。 */
	@Column(name = "objective_score", precision = 20, scale = 4)
	private BigDecimal objectiveScore;

	/** 规则快照（JSON）。 */
	@Lob
	@Column(name = "rule_snapshot_json")
	private String ruleSnapshotJson;

	/** 输入数据快照（JSON）。 */
	@Lob
	@Column(name = "input_snapshot_json")
	private String inputSnapshotJson;

	/** 结果摘要（JSON）。 */
	@Lob
	@Column(name = "summary_json")
	private String summaryJson;

	/** 排程分配明细。 */
	@JsonIgnore
	@OneToMany(mappedBy = "scheduleResult", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private Set<ScheduleAssignment> assignments;

	/** 标记运行开始。 */
	public void markRunning() {
		this.scheduleStatus = ScheduleStatus.RUNNING;
		this.startedAt = LocalDateTime.now();
	}

	/**
	 * 标记运行成功并记录评分。
	 *
	 * @param hard 硬约束得分
	 * @param soft 软约束得分
	 */
	public void markSucceeded(BigDecimal hard, BigDecimal soft) {
		this.hardScore = hard;
		this.softScore = soft;
		this.objectiveScore = sum(hard, soft);
		this.scheduleStatus = ScheduleStatus.SUCCEEDED;
		this.finishedAt = LocalDateTime.now();
	}

	/**
	 * 标记运行失败。
	 *
	 * @param reason 失败原因
	 */
	public void markFailed(String reason) {
		this.scheduleStatus = ScheduleStatus.FAILED;
		this.finishedAt = LocalDateTime.now();
		this.summaryJson = reason;
	}

	/** 发布结果。 */
	public void publish() {
		if (this.scheduleStatus != ScheduleStatus.SUCCEEDED) {
			throw new IllegalStateException("只有 SUCCEEDED 的结果可发布");
		}
		this.scheduleStatus = ScheduleStatus.PUBLISHED;
	}

	/** 取消排程。 */
	public void cancel() {
		if (this.scheduleStatus == ScheduleStatus.PUBLISHED) {
			throw new IllegalStateException("已发布结果不可取消");
		}
		this.scheduleStatus = ScheduleStatus.CANCELLED;
	}

	private BigDecimal sum(BigDecimal a, BigDecimal b) {
		BigDecimal left = a == null ? BigDecimal.ZERO : a;
		BigDecimal right = b == null ? BigDecimal.ZERO : b;
		return left.add(right);
	}
}
