package com.chronos.education.scheduling.model;

import java.time.LocalDate;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 具体日期的课表例外；周期课表保持不变，便于审计和恢复。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_schedule_date_exception")
public class ScheduleDateException extends BaseEntity {
	@Column(name = "semester_code", length = 32, nullable = false)
	private String semesterCode;

	@Column(name = "source_entry_id", length = 64, nullable = false)
	private String sourceEntryId;

	@Column(name = "source_date", nullable = false)
	private LocalDate sourceDate;

	@Column(name = "exception_type", length = 24, nullable = false)
	private String exceptionType;

	@Column(name = "target_date")
	private LocalDate targetDate;

	@Column(name = "target_period_no")
	private Integer targetPeriodNo;

	@Column(name = "target_classroom_id", length = 64)
	private String targetClassroomId;

	@Column(name = "substitute_teacher_id", length = 64)
	private String substituteTeacherId;

	/** 审批流程实例。用于保证 Flowable 完成事件重复投递时不会重复生成日期例外。 */
	@Column(name = "workflow_instance_id", length = 64, unique = true)
	private String workflowInstanceId;

	@Column(name = "reason", length = 500, nullable = false)
	private String reason;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
