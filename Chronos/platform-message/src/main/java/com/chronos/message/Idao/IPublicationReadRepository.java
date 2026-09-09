package com.chronos.message.Idao;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.PublicationRead;

public interface IPublicationReadRepository extends JpaRepository<PublicationRead, String> {
	Optional<PublicationRead> findByPublicationIdAndUsername(String publicationId, String username);

	long countByPublicationId(String publicationId);

	List<PublicationRead> findByPublicationId(String publicationId);

	void deleteByPublicationId(String publicationId);
}
