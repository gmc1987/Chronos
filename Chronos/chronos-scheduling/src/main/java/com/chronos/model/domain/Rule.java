package com.chronos.model.domain;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import com.chronos.model.domain.enums.RuleScope;
import com.chronos.model.domain.enums.RuleType;
import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 排程规则实体。
 * <p>
 * 规则可以是硬约束（必须满足）或软约束（尽量满足），并可以作用于不同范围。
 */
@SuppressWarnings("serial")
@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "d_schedule_rule", comment = "领域-排程规则")
public class Rule extends BaseEntity {

	/** 所属组织。 */
	@Column(name = "organization_id", nullable = false, length = 64)
	private String organizationId;

	/** 规则名称。 */
	@Column(name = "rule_name", length = 120, nullable = false)
	private String ruleName;

	/** 规则编码。 */
	@Column(name = "rule_code", length = 100, nullable = false)
	private String ruleCode;

	/** 规则类型（HARD/SOFT）。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "rule_type", nullable = false, length = 16)
	private RuleType ruleType;

	/** 规则作用范围。 */
	@Enumerated(EnumType.STRING)
	@Column(name = "rule_scope", nullable = false, length = 32)
	private RuleScope ruleScope;

	/** 作用组织单元（当 ruleScope 为组织单元时使用）。 */
	@Column(name = "organization_unit_id", length = 64)
	private String organizationUnitId;

	/** 是否启用。 */
	@Column(name = "enabled", nullable = false)
	private Boolean enabled = true;

	/** 权重（软约束优化时使用）。 */
	@Column(name = "weight")
	private Integer weight;

	/** 规则引擎类型（如 spel / drools / custom）。 */
	@Column(name = "rule_engine", length = 50)
	private String ruleEngine;

	/** 规则表达式（DSL/JSON/脚本）。 */
	@Lob
	@Column(name = "rule_expression", nullable = false)
	private String ruleExpression;

	/** 描述。 */
	@Column(name = "description", length = 500)
	private String description;

	/** 启用规则。 */
	public void enable() {
		this.enabled = true;
	}

	/** 停用规则。 */
	public void disable() {
		this.enabled = false;
	}

	/**
	 * 判断是否硬约束。
	 *
	 * @return true=硬约束
	 */
	public boolean isHardConstraint() {
		return this.ruleType == RuleType.HARD;
	}

	/**
	 * 判断规则是否全局范围。
	 *
	 * @return true=全局范围
	 */
	public boolean isGlobalScope() {
		return this.ruleScope == RuleScope.GLOBAL;
	}
}
