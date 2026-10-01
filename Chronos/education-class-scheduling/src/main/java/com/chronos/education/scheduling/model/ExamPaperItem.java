package com.chronos.education.scheduling.model;

import java.math.BigDecimal;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 一场考试的试卷题目快照；分析不依赖会继续编辑的题库版本。 */
@Entity
@Getter
@Setter
@Table(name = "edu_exam_paper_item")
public class ExamPaperItem extends BaseEntity {
	@Column(name = "session_id", nullable = false, length = 64)
	private String sessionId;

	@Column(name = "question_no", nullable = false, length = 32)
	private String questionNo;

	/**
	 * 关联题库主记录。试卷仍保存题干和满分快照，题库后续修订不会改变已发布试卷；
	 * 此字段仅用于追溯知识点映射。
	 */
	@Column(name = "question_id", length = 64)
	private String questionId;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "max_score", nullable = false, precision = 8, scale = 2)
	private BigDecimal maxScore;
}
