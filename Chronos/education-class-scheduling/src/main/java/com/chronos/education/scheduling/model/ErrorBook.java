package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 学生错题册只保存来源引用和解析，不复制作业提交、成绩模型。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_error_book")
public class ErrorBook extends BaseEntity {
	@Column(name = "student_id", nullable = false, length = 64)
	private String studentId;
	@Column(nullable = false, length = 200)
	private String name;
	@Column(nullable = false)
	private boolean archived;
}
