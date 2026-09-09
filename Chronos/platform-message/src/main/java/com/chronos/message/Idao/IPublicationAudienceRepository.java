package com.chronos.message.Idao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.PublicationAudience;

public interface IPublicationAudienceRepository extends JpaRepository<PublicationAudience, String> {
	List<PublicationAudience> findByPublicationId(String publicationId);

	void deleteByPublicationId(String publicationId);
}
