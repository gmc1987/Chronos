package com.chronos.message.Idao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.PublicationVersion;

public interface IPublicationVersionRepository extends JpaRepository<PublicationVersion, String> {
	List<PublicationVersion> findByPublicationIdOrderByVersionNoDesc(String publicationId);

	long countByPublicationId(String publicationId);

	Optional<PublicationVersion> findByPublicationIdAndVersionNo(String publicationId, Integer versionNo);
}
