package com.chronos.education.classgroup.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_class_group_audit")
public class ClassGroupAudit extends BaseEntity {
	@Column(name = "group_id", length = 64, nullable = false)
	private String groupId;
	@Column(name = "action", length = 48, nullable = false)
	private String action;
	@Column(name = "member_type", length = 24)
	private String memberType;
	@Column(name = "member_id", length = 64)
	private String memberId;
	@Column(name = "detail", columnDefinition = "text")
	private String detail;
	@Column(name = "actor", length = 128, nullable = false)
	private String actor;
}
