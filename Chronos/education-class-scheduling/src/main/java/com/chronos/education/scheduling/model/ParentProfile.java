package com.chronos.education.scheduling.model;

import com.chronos.model.pojo.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 家长档案只保存家校业务所需信息，账号绑定在后续门户身份层完成。 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "edu_parent_profile")
public class ParentProfile extends BaseEntity {
	@Column(name = "parent_no", length = 64, nullable = false, unique = true)
	private String parentNo;

	@Column(name = "parent_name", length = 128, nullable = false)
	private String parentName;

	@Column(name = "gender", length = 16)
	private String gender;

	@Column(name = "phone", length = 32, nullable = false)
	private String phone;

	@Column(name = "employment", length = 128)
	private String employment;

	@Column(name = "status", length = 24, nullable = false)
	private String status = "ACTIVE";
}
