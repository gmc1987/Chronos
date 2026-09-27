package com.chronos.Idao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.pojo.IdentitySource;

public interface IIdentitySourceRepository extends JpaRepository<IdentitySource, String> {
	Optional<IdentitySource> findBySourceCode(String sourceCode);
}
