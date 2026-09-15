package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 学科考试监考资格，由考务管理维护，不从教师专业字符串推断。 */
@Entity
@Getter
@Setter
@Table(name = "edu_exam_teacher_qualification")
public class ExamTeacherQualification extends BaseEntity {
	@Column(name = "teacher_id", nullable = false, length = 64)
	private String teacherId;

	@Column(name = "subject_id", nullable = false, length = 64)
	private String subjectId;
}
