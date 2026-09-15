package com.chronos.education.scheduling.model;

import java.time.LocalDate;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 审批单中的课程发生日；审批后记录生成的课表例外 ID。 */
@Entity
@Getter
@Setter
@Table(name = "edu_exam_course_suspension_item")
public class ExamCourseSuspensionItem extends BaseEntity {
	@Column(name = "request_id", nullable = false, length = 64)
	private String requestId;

	@Column(name = "source_entry_id", nullable = false, length = 64)
	private String sourceEntryId;

	@Column(name = "source_date", nullable = false)
	private LocalDate sourceDate;

	@Column(name = "exception_id", length = 64)
	private String exceptionId;
}
