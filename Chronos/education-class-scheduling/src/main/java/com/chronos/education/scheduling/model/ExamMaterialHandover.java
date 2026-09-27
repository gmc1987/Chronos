package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="edu_exam_material_handover")
@Getter @Setter
public class ExamMaterialHandover extends BaseEntity {
	@Column(name="ledger_id", nullable=false, length=64) private String ledgerId;
	@Column(name="handover_by", nullable=false, length=128) private String handoverBy;
	@Column(name="received_by", nullable=false, length=128) private String receivedBy;
	@Column(name="handed_at", nullable=false) private LocalDateTime handedAt;
	@Column(nullable=false) private Integer quantity;
	@Column(name="difference_reason", length=1000) private String differenceReason;
}
