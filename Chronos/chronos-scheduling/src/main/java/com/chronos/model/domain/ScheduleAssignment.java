package com.chronos.model.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 排程分配明细实体。
 * <p>
 * 描述某个任务在某段时间被分配给哪个资源执行。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_schedule_assignment", comment = "领域-排程明细")
public class ScheduleAssignment extends BaseEntity {

	/** 所属排程结果。 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "schedule_result_id", nullable = false, referencedColumnName = "id")
	private ScheduleResult scheduleResult;

	/** 关联任务。 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_id", nullable = false, referencedColumnName = "id")
	private Task task;

	/** 执行资源。 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resource_id", nullable = false, referencedColumnName = "id")
	private SchedulableResource resource;

	/** 组织单元（可选）。 */
	@Column(name = "organization_unit_id", length = 64)
	private String organizationUnitId;

	/** 分配开始时间。 */
	@Column(name = "start_time", nullable = false)
	private LocalDateTime startTime;

	/** 分配结束时间。 */
	@Column(name = "end_time", nullable = false)
	private LocalDateTime endTime;

	/** 是否锁定（锁定后排程器不再调整）。 */
	@Column(name = "locked", nullable = false)
	private Boolean locked = false;

	/** 违规标签（逗号分隔或JSON简串）。 */
	@Column(name = "violation_tags", length = 500)
	private String violationTags;

	/** 锁定该分配。 */
	public void lock() {
		this.locked = true;
	}

	/** 解锁该分配。 */
	public void unlock() {
		this.locked = false;
	}

	/**
	 * 更新时间段。
	 *
	 * @param start 开始时间
	 * @param end   结束时间
	 */
	public void updateTimeRange(LocalDateTime start, LocalDateTime end) {
		if (Boolean.TRUE.equals(this.locked)) {
			throw new IllegalStateException("分配已锁定，不能调整时间");
		}
		if (start == null || end == null || !start.isBefore(end)) {
			throw new IllegalArgumentException("开始时间必须早于结束时间");
		}
		this.startTime = start;
		this.endTime = end;
	}

	/**
	 * 判断与另一条分配是否时间重叠（同资源冲突判断时可复用）。
	 *
	 * @param other 另一条分配
	 * @return true=重叠
	 */
	public boolean overlaps(ScheduleAssignment other) {
		if (other == null || this.startTime == null || this.endTime == null || other.startTime == null || other.endTime == null) {
			return false;
		}
		return this.startTime.isBefore(other.endTime) && other.startTime.isBefore(this.endTime);
	}
}
