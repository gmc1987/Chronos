package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 考试场次与真实课程开设的显式映射；没有映射时下游成绩事件必须拒绝发布。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_exam_session_offering", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_exam_session_offering", columnNames = { "session_id", "offering_id" }))
public class ExamSessionOffering extends BaseEntity {
	@Column(name = "session_id", nullable = false, length = 64)
	private String sessionId;

	@Column(name = "offering_id", nullable = false, length = 64)
	private String offeringId;
}
