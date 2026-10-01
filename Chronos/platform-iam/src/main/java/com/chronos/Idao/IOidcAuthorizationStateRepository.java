package com.chronos.Idao;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.model.pojo.OidcAuthorizationState;

public interface IOidcAuthorizationStateRepository extends JpaRepository<OidcAuthorizationState, String> {
	@Modifying
	@Transactional
	@Query("""
			update OidcAuthorizationState state
			   set state.consumedAt = :consumedAt
			 where state.state = :stateValue
			   and state.consumedAt is null
			   and state.expiresAt > :consumedAt
			""")
	int consume(@Param("stateValue") String stateValue, @Param("consumedAt") LocalDateTime consumedAt);

	@Modifying
	@Transactional
	int deleteByExpiresAtBefore(LocalDateTime cutoff);
}
