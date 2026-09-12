package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 题库题目主记录；选项和知识点关联通过独立表维护。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_question")
public class Question extends BaseEntity {
	@Column(name = "bank_id", nullable = false, length = 64)
	private String bankId;
	@Column(name = "question_type", nullable = false, length = 24)
	private String questionType;
	@Column(length = 16)
	private String difficulty;
	@Column(nullable = false, columnDefinition = "text")
	private String stem;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(columnDefinition = "text")
	private String answer;
	@Column(columnDefinition = "text")
	private String analysis;
}
