package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 将平台登录账号稳定绑定到教师、学生或家长领域档案。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
		name = "edu_user_profile_binding",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_edu_user_profile_binding",
				columnNames = { "username", "profile_type" }))
public class EducationUserBinding extends BaseEntity {
	@Column(name = "username", length = 100, nullable = false)
	private String username;

	@Column(name = "profile_type", length = 24, nullable = false)
	private String profileType;

	@Column(name = "profile_id", length = 64, nullable = false)
	private String profileId;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
