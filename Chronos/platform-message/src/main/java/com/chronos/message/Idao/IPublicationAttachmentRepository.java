package com.chronos.message.Idao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.message.model.PublicationAttachment;

public interface IPublicationAttachmentRepository extends JpaRepository<PublicationAttachment, String> {
	List<PublicationAttachment> findByPublicationIdOrderByCreateTimeAsc(String publicationId);
}
