package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter @NoArgsConstructor
@Table(name = "edu_teaching_review_record", indexes = {
		@Index(name = "uq_teaching_review_resource", columnList = "resource_type,resource_id", unique = true),
		@Index(name = "idx_teaching_review_workflow", columnList = "workflow_instance_id") })
public class TeachingReviewRecord extends BaseEntity {
	@Column(name="resource_type", nullable=false, length=32) private String resourceType;
	@Column(name="resource_id", nullable=false, length=64) private String resourceId;
	@Column(name="offering_id", length=64) private String offeringId;
	@Column(name="business_key", nullable=false, length=160) private String businessKey;
	@Column(name="workflow_instance_id", nullable=false, length=64) private String workflowInstanceId;
	@Column(name="status", nullable=false, length=24) private String status = "SUBMITTED";
	@Column(name="decision", length=24) private String decision;
	@Column(name="comment", length=2000) private String comment;
}
