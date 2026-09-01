package com.chronos.model.domain;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.chronos.model.domain.enums.CapabilityLevel;
import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 任务能力需求实体。
 * <p>
 * 用于表达任务对能力的最小等级和数量要求，供排程引擎做资格匹配。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_task_capability_requirement", comment = "领域-任务能力需求")
public class TaskCapabilityRequirement extends BaseEntity {

	/** 所属任务。 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "task_id", nullable = false, referencedColumnName = "id")
	private Task task;

	/** 需求能力。 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "capability_id", nullable = false, referencedColumnName = "id")
	private Capability capability;

	/** 最低能力等级。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "minimum_level", nullable = false, length = 16)
	private CapabilityLevel minimumLevel = CapabilityLevel.L1;

	/** 该能力要求的人/机数量。 */
	@Column(name = "required_count", nullable = false)
	private Integer requiredCount = 1;

	/** 校验需求参数。 */
	public void validate() {
		if (this.requiredCount == null || this.requiredCount <= 0) {
			throw new IllegalStateException("requiredCount 必须大于 0");
		}
		if (this.minimumLevel == null) {
			throw new IllegalStateException("minimumLevel 不能为空");
		}
	}
}
