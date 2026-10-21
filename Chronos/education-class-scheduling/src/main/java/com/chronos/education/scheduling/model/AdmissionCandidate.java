package com.chronos.education.scheduling.model;

import java.time.LocalDateTime;
import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 省级招办录取数据在校内的落地记录；通过资格复查后才能转为正式学生档案。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_admission_candidate", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_admission_candidate", columnNames = {"admission_year", "candidate_no"}))
public class AdmissionCandidate extends BaseEntity {
	@Column(name = "source_batch_no", length = 64, nullable = false) private String sourceBatchNo;
	@Column(name = "source_type", length = 24, nullable = false) private String sourceType = "PROVINCIAL_IMPORT";
	@Column(name = "candidate_no", length = 64, nullable = false) private String candidateNo;
	@Column(name = "admission_no", length = 64) private String admissionNo;
	@Column(name = "student_name", length = 128, nullable = false) private String studentName;
	@Column(name = "gender", length = 16) private String gender;
	@Column(name = "phone", length = 32) private String phone;
	@Column(name = "admission_year", nullable = false) private Integer admissionYear;
	@Column(name = "education_level", length = 32, nullable = false) private String educationLevel;
	@Column(name = "admitted_major_id", length = 64) private String admittedMajorId;
	@Column(name = "planned_class_id", length = 64) private String plannedClassId;
	@Column(name = "admission_status", length = 24, nullable = false) private String admissionStatus = "ADMITTED";
	@Column(name = "reviewed_by", length = 128) private String reviewedBy;
	@Column(name = "reviewed_at") private LocalDateTime reviewedAt;
	@Column(name = "review_remark", length = 500) private String reviewRemark;
	@Column(name = "checked_in_at") private LocalDateTime checkedInAt;
	@Column(name = "student_id", length = 64, unique = true) private String studentId;
	@Column(name = "registration_status", length = 24, nullable = false) private String registrationStatus = "NOT_SUBMITTED";
	@Column(name = "national_student_no", length = 64) private String nationalStudentNo;
	@Column(name = "registered_at") private LocalDateTime registeredAt;
	@Column(name = "registration_error", length = 500) private String registrationError;
	@Version @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;
}
