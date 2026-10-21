package com.chronos.facility.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_repair_order_event", comment = "报修单状态流转记录")
public class RepairOrderEvent extends BaseEntity {
	@Column(name = "repair_order_id", nullable = false, length = 64)
	private String repairOrderId;
	@Column(name = "event_type", nullable = false, length = 32)
	private String eventType;
	@Column(name = "from_status", length = 32)
	private String fromStatus;
	@Column(name = "to_status", nullable = false, length = 32)
	private String toStatus;
	@Column(name = "operator_username", nullable = false, length = 100)
	private String operatorUsername;
	@Column(name = "remark", length = 1000)
	private String remark;
}
