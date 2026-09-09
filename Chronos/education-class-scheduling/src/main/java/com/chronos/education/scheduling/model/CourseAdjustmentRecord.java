package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** 调课审批落地记录；工作流实例唯一约束保证重复事件不会重复修改课表。 */
@Entity
@Getter
@Setter
@Table(
		name = "edu_course_adjustment_record",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_adjustment_workflow_instance",
				columnNames = "workflow_instance_id"))
public class CourseAdjustmentRecord extends BaseEntity {
	@Column(name = "workflow_instance_id", length = 64, nullable = false)
	private String workflowInstanceId;

	@Column(name = "business_key", length = 128)
	private String businessKey;

	@Column(name = "schedule_entry_id", length = 64, nullable = false)
	private String scheduleEntryId;

	@Column(name = "adjustment_type", length = 24, nullable = false)
	private String adjustmentType;

	@Column(name = "result_entry_id", length = 64)
	private String resultEntryId;

	@Column(name = "status", length = 24, nullable = false)
	private String status;

	@Column(name = "message", length = 1000)
	private String message;
}
