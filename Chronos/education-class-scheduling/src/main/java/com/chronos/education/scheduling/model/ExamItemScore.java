package com.chronos.education.scheduling.model;

import java.math.BigDecimal;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 考生逐题实得分；同一题同一考生只保留一个当前分数。 */
@Entity
@Getter
@Setter
@Table(name = "edu_exam_item_score")
public class ExamItemScore extends BaseEntity {
	@Column(name = "item_id", nullable = false, length = 64)
	private String itemId;

	@Column(name = "candidate_id", nullable = false, length = 64)
	private String candidateId;

	@Column(name = "score", nullable = false, precision = 8, scale = 2)
	private BigDecimal score;
}
