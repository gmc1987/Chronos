package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 教师任职异动历史；教师主档只保存当前已经生效的任职状态。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_teacher_employment_change")
public class TeacherEmploymentChange extends BaseEntity {
	@Column(name = "teacher_id", nullable = false, length = 64)
	private String teacherId;
	@Column(name = "change_type", nullable = false, length = 24)
	private String changeType;
	@Column(name = "from_status", nullable = false, length = 24)
	private String fromStatus;
	@Column(name = "to_status", nullable = false, length = 24)
	private String toStatus;
	@Column(name = "from_department_id", length = 64)
	private String fromDepartmentId;
	@Column(name = "to_department_id", length = 64)
	private String toDepartmentId;
	@Column(name = "effective_date", nullable = false)
	private LocalDate effectiveDate;
	@Column(name = "reason", nullable = false, length = 1000)
	private String reason;
	@Column(name = "status", nullable = false, length = 24)
	private String status;
	@Column(name = "created_by", nullable = false, length = 128)
	private String createdBy;
	@Column(name = "applied_at")
	private LocalDateTime appliedAt;
	@Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
}
