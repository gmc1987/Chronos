package com.chronos.Idao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.model.pojo.ExternalIdentity;

public interface IExternalIdentityRepository extends JpaRepository<ExternalIdentity, String> {
	Optional<ExternalIdentity> findBySourceIdAndExternalSubject(String sourceId, String externalSubject);
	List<ExternalIdentity> findByUserIdOrderByLinkedAtDesc(String userId);
	boolean existsBySourceIdAndExternalSubject(String sourceId, String externalSubject);
}
