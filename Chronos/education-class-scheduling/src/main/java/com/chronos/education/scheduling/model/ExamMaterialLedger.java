package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="edu_exam_material_ledger")
@Getter @Setter
public class ExamMaterialLedger extends BaseEntity {
	@Column(name="session_id", nullable=false, length=64) private String sessionId;
	@Column(name="material_type", nullable=false, length=64) private String materialType;
	@Column(name="batch_no", nullable=false, length=128) private String batchNo;
	@Column(name="planned_quantity", nullable=false) private Integer plannedQuantity;
	@Column(name="received_quantity", nullable=false) private Integer receivedQuantity = 0;
	@Column(name="seal_no", length=128) private String sealNo;
	@Column(nullable=false, length=24) private String status = "OPEN";
	@Column(name="difference_reason", length=1000) private String differenceReason;
}
