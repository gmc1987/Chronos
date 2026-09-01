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
@Table(name = "t_audit_log", comment = "Audit log")
public class AuditLog extends BaseEntity {
	private static final long serialVersionUID = 1L;

	@Column(name = "username", length = 200)
	private String username;

	@Column(name = "action", length = 200)
	private String action;

	@Column(name = "detail", length = 2000)
	private String detail;

}
