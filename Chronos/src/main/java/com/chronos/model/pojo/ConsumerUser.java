package com.chronos.model.pojo;

import org.hibernate.annotations.DynamicInsert;
import org.hibernate.annotations.DynamicUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DynamicUpdate
@DynamicInsert
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_consumer_user", comment = "用户表")
public class ConsumerUser extends BaseEntity {
	private static final long serialVersionUID = 1L;
	@Column(name = "username", length = 100, nullable = false, unique = true)
	private String username;

	@Column(name = "password", length = 200, nullable = false)
	private String password;
	@Column(name = "email", length = 200, nullable = true)
	private String email;
	@Column(name = "phone", length = 32, nullable = true, unique = true)
	private String phone;

	@Column(name = "status", nullable = false)
	private Integer status;

	@Column(name = "customer_type", nullable = false, length = 50)
	private String customerType = "0";
}
