package com.chronos.ai.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.ai.model.AiUsagePolicy;

import jakarta.persistence.LockModeType;

public interface AiUsagePolicyRepository extends JpaRepository<AiUsagePolicy, String> {
	Optional<AiUsagePolicy> findByScopeTypeAndScopeKey(String scopeType, String scopeKey);
	List<AiUsagePolicy> findAllByOrderByScopeTypeAscScopeKeyAsc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select policy from AiUsagePolicy policy
			where policy.enabled = true and (
				(policy.scopeType = 'GLOBAL' and policy.scopeKey = '*')
				or (policy.scopeType = 'MODEL' and policy.scopeKey = :modelId)
				or (policy.scopeType = 'USER' and policy.scopeKey = :username)
			)
			order by policy.scopeType, policy.scopeKey
			""")
	List<AiUsagePolicy> findApplicableForUpdate(
			@Param("modelId") String modelId,
			@Param("username") String username);
}
