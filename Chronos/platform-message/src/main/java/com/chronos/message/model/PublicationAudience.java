package com.chronos.message.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 发布受众。多个正向条件按 OR 合并，排除条件最后统一扣除。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "msg_publication_audience",
		comment = "通知公告受众",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_msg_publication_audience",
				columnNames = { "publication_id", "subject_type", "subject_id", "excluded" }))
public class PublicationAudience extends BaseEntity {
	@Column(name = "publication_id", length = 64, nullable = false)
	private String publicationId;

	@Column(name = "subject_type", length = 32, nullable = false)
	private String subjectType;

	@Column(name = "subject_id", length = 128, nullable = false)
	private String subjectId;

	@Column(name = "include_children", nullable = false)
	private Boolean includeChildren = false;

	@Column(name = "excluded", nullable = false)
	private Boolean excluded = false;
}
