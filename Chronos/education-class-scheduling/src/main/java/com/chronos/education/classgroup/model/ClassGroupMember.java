package com.chronos.education.classgroup.model;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_class_group_member", uniqueConstraints = @UniqueConstraint(
		name = "uk_edu_class_group_member", columnNames = { "group_id", "member_type", "member_id" }))
public class ClassGroupMember extends BaseEntity {
	@Column(name = "group_id", length = 64, nullable = false)
	private String groupId;
	@Column(name = "member_type", length = 24, nullable = false)
	private String memberType;
	@Column(name = "member_id", length = 64, nullable = false)
	private String memberId;
	@Column(name = "username", length = 128)
	private String username;
	@Column(name = "role", length = 24, nullable = false)
	private String role = "MEMBER";
	@Column(name = "source", length = 24, nullable = false)
	private String source = "DERIVED";
	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
