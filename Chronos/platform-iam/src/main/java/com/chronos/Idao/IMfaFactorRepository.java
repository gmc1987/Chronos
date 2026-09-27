package com.chronos.Idao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.model.pojo.MfaFactor;

public interface IMfaFactorRepository extends JpaRepository<MfaFactor, String> {
	Optional<MfaFactor> findByUserIdAndFactorType(String userId, String factorType);

	@Modifying
	@Transactional
	@Query("""
			update MfaFactor factor
			   set factor.lastUsedTimeStep = :timeStep
			 where factor.id = :factorId
			   and (factor.lastUsedTimeStep is null or factor.lastUsedTimeStep < :timeStep)
			""")
	int claimTimeStep(@Param("factorId") String factorId, @Param("timeStep") long timeStep);
}
