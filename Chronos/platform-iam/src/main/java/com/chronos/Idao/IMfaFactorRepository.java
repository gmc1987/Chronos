package com.chronos.Idao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.pojo.MfaFactor;

public interface IMfaFactorRepository extends JpaRepository<MfaFactor, String> {
	Optional<MfaFactor> findByUserIdAndFactorType(String userId, String factorType);
}
