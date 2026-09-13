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
	@Column(precision = 6, scale = 2)
	private java.math.BigDecimal score;
	@Column(nullable = false)
	private boolean objective;
	@Column(name = "answer_schema_json", columnDefinition = "jsonb")
	private String answerSchemaJson;
	@Column(length = 32)
	private String source;
	@Column(name = "usable_from")
	private java.time.LocalDateTime usableFrom;
	@Column(name = "usable_until")
	private java.time.LocalDateTime usableUntil;
	@Column(name = "published_version_id", length = 64)
	private String publishedVersionId;
	@Column(name = "current_version_no", nullable = false)
	private Integer currentVersionNo = 0;
	@jakarta.persistence.Version
	@Column(name = "row_version", nullable = false)
	private Long rowVersion = 0L;
	@Column(nullable = false)
	private boolean archived;
}
