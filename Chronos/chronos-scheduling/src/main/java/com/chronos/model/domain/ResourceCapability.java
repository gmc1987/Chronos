package com.chronos.model.domain;

import java.time.LocalDate;
import java.util.Objects;

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
 * 资源-能力绑定实体。
 * <p>
 * 记录某资源拥有哪些能力、能力等级、有效期与评分等信息。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_resource_capability", comment = "领域-资源能力绑定")
public class ResourceCapability extends BaseEntity {

	/** 资源。 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "resource_id", nullable = false, referencedColumnName = "id")
	private SchedulableResource resource;

	/** 能力。 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "capability_id", nullable = false, referencedColumnName = "id")
	private Capability capability;

	/** 能力等级。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "capability_level", nullable = false, length = 16)
	private CapabilityLevel capabilityLevel;

	/** 能力评分（可选，用于资源优选）。 */
	@Column(name = "score")
	private Integer score;

	/** 生效起始日。 */
	@Column(name = "effective_from")
	private LocalDate effectiveFrom;

	/** 生效截止日。 */
	@Column(name = "effective_to")
	private LocalDate effectiveTo;

	/**
	 * 更新有效区间。
	 *
	 * @param from 起始日期
	 * @param to   截止日期
	 */
	public void setEffectiveRange(LocalDate from, LocalDate to) {
		if (from != null && to != null && from.isAfter(to)) {
			throw new IllegalArgumentException("能力有效起始时间不能晚于截止时间");
		}
		this.effectiveFrom = from;
		this.effectiveTo = to;
	}

	/**
	 * 判断指定日期能力是否有效。
	 *
	 * @param date 日期
	 * @return true=有效
	 */
	public boolean isEffectiveOn(LocalDate date) {
		Objects.requireNonNull(date, "date 不能为空");
		if (this.effectiveFrom != null && date.isBefore(this.effectiveFrom)) {
			return false;
		}
		if (this.effectiveTo != null && date.isAfter(this.effectiveTo)) {
			return false;
		}
		return true;
	}
}
