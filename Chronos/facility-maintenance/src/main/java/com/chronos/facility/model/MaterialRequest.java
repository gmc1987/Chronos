package com.chronos.facility.model;

import java.time.LocalDateTime;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "fm_material_request", comment = "维修物料申请单")
public class MaterialRequest extends BaseEntity {
	@Column(name = "request_no", nullable = false, unique = true, length = 32)
	private String requestNo;
	@Column(name = "repair_order_id", nullable = false, length = 64)
	private String repairOrderId;
	@Column(name = "applicant_username", nullable = false, length = 100)
	private String applicantUsername;
	@Column(name = "applicant_name", nullable = false, length = 100)
	private String applicantName;
	@Column(name = "status", nullable = false, length = 24)
	private String status = "SUBMITTED";
	@Column(name = "remark", length = 1000)
	private String remark;
	@Column(name = "reviewed_by", length = 100)
	private String reviewedBy;
	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;
	@Column(name = "review_comment", length = 1000)
	private String reviewComment;
	@Column(name = "issued_by", length = 100)
	private String issuedBy;
	@Column(name = "issued_at")
	private LocalDateTime issuedAt;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
