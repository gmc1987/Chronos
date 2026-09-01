package com.chronos.model.domain;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.chronos.model.domain.enums.ResourceType;
import com.chronos.model.domain.enums.TaskPriority;
import com.chronos.model.domain.enums.TaskStatus;
import com.chronos.model.pojo.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 任务聚合根。
 * <p>
 * 表达“需要被安排执行的工作单元”，包括时间窗、时长、优先级、资源类型要求、能力要求等。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_task", comment = "领域-任务")
public class Task extends BaseEntity {

	/** 所属组织。 */
	@Column(name = "organization_id", nullable = false, length = 64)
	private String organizationId;

	/** 所属组织单元（可选）。 */
	@Column(name = "organization_unit_id", length = 64)
	private String organizationUnitId;

	/** 任务名称。 */
	@Column(name = "task_name", length = 200, nullable = false)
	private String taskName;

	/** 任务编码。 */
	@Column(name = "task_code", length = 100, nullable = false)
	private String taskCode;

	/** 需要资源数量。 */
	@Column(name = "required_count", nullable = false)
	private Integer requiredCount = 1;

	/** 所需资源类型。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "required_resource_type", nullable = false, length = 32)
	private ResourceType requiredResourceType;

	/** 最早开始时间。 */
	@Column(name = "earliest_start_time")
	private LocalDateTime earliestStartTime;

	/** 最晚结束时间。 */
	@Column(name = "latest_end_time")
	private LocalDateTime latestEndTime;

	/** 任务持续时长（分钟）。 */
	@Column(name = "duration_minutes", nullable = false)
	private Integer durationMinutes;

	/** 优先级。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "priority", nullable = false, length = 16)
	private TaskPriority priority = TaskPriority.MEDIUM;

	/** 任务状态。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "task_status", nullable = false, length = 16)
	private TaskStatus taskStatus = TaskStatus.DRAFT;

	/** 是否允许抢占。 */
	@Column(name = "preemptive", nullable = false)
	private Boolean preemptive = false;

	/** 描述。 */
	@Column(name = "description", length = 500)
	private String description;

	/** 任务能力需求集合。 */
	@JsonIgnore
	@OneToMany(mappedBy = "task", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	private Set<TaskCapabilityRequirement> capabilityRequirements;

	/** 将任务置为可排程状态。 */
	public void ready() {
		validateTimeWindow();
		if (this.durationMinutes == null || this.durationMinutes <= 0) {
			throw new IllegalStateException("任务时长必须大于 0");
		}
		if (this.requiredCount == null || this.requiredCount <= 0) {
			throw new IllegalStateException("任务所需资源数必须大于 0");
		}
		this.taskStatus = TaskStatus.READY;
	}

	/** 标记为已入排程。 */
	public void markScheduled() {
		if (this.taskStatus != TaskStatus.READY) {
			throw new IllegalStateException("只有 READY 状态任务可标记为 SCHEDULED");
		}
		this.taskStatus = TaskStatus.SCHEDULED;
	}

	/** 标记任务开始执行。 */
	public void start() {
		if (this.taskStatus != TaskStatus.SCHEDULED) {
			throw new IllegalStateException("只有 SCHEDULED 状态任务可开始执行");
		}
		this.taskStatus = TaskStatus.RUNNING;
	}

	/** 标记任务完成。 */
	public void complete() {
		if (this.taskStatus != TaskStatus.RUNNING) {
			throw new IllegalStateException("只有 RUNNING 状态任务可完成");
		}
		this.taskStatus = TaskStatus.DONE;
	}

	/** 取消任务。 */
	public void cancel() {
		this.taskStatus = TaskStatus.CANCELLED;
	}

	/**
	 * 更新时间窗。
	 *
	 * @param earliest 最早开始
	 * @param latest   最晚结束
	 */
	public void updateTimeWindow(LocalDateTime earliest, LocalDateTime latest) {
		this.earliestStartTime = earliest;
		this.latestEndTime = latest;
		validateTimeWindow();
	}

	/**
	 * 判断开始时间是否落入任务时间窗。
	 *
	 * @param start 开始时间
	 * @return true=允许
	 */
	public boolean canStartAt(LocalDateTime start) {
		Objects.requireNonNull(start, "start 不能为空");
		if (this.earliestStartTime != null && start.isBefore(this.earliestStartTime)) {
			return false;
		}
		if (this.latestEndTime != null && this.durationMinutes != null) {
			LocalDateTime end = start.plusMinutes(this.durationMinutes);
			if (end.isAfter(this.latestEndTime)) {
				return false;
			}
		}
		return true;
	}

	private void validateTimeWindow() {
		if (this.earliestStartTime != null && this.latestEndTime != null
				&& !this.earliestStartTime.isBefore(this.latestEndTime)) {
			throw new IllegalStateException("任务时间窗非法：earliestStartTime 必须早于 latestEndTime");
		}
	}
}
