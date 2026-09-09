package com.chronos.message.Idao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.PublicationRecipient;

public interface IPublicationRecipientRepository extends JpaRepository<PublicationRecipient, String> {
	boolean existsByPublicationIdAndUsername(String publicationId, String username);

	long countByPublicationId(String publicationId);

	List<PublicationRecipient> findByPublicationId(String publicationId);

	void deleteByPublicationId(String publicationId);

	Optional<PublicationRecipient> findByPublicationIdAndUsername(String publicationId, String username);
}
