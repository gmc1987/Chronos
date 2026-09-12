package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 题库聚合根；题目、选项、知识点关联独立建模，不接入作业提交/评分。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_question_bank")
public class QuestionBank extends BaseEntity {
	@Column(name = "offering_id", length = 64)
	private String offeringId;
	@Column(nullable = false, length = 200)
	private String name;
	@Column(length = 128)
	private String subject;
	@Column(nullable = false, length = 24)
	private String status = "DRAFT";
	@Column(nullable = false)
	private boolean archived;
}
