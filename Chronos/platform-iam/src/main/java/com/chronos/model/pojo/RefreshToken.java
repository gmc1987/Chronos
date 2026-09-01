package com.chronos.model.pojo;

import java.time.LocalDateTime;

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
@DynamicInsert
@DynamicUpdate
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Table(name = "t_refresh_token", comment = "Refresh token store")
public class RefreshToken extends BaseEntity {
	private static final long serialVersionUID = 1L;
	
	@Column(name = "token", length = 1000, nullable = false, unique = true)
	private String token;

	@Column(name = "username", length = 200, nullable = false)
	private String username;
	
	@Column(name = "expiry_time", nullable = false)
	private LocalDateTime expiryTime;

	@Column(name = "revoked", nullable = false)
	private Boolean revoked = Boolean.valueOf(false);

}
