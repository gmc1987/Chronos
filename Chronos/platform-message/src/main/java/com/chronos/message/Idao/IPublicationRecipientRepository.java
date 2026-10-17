package com.chronos.message.Idao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.message.model.PublicationRecipient;

public interface IPublicationRecipientRepository extends JpaRepository<PublicationRecipient, String> {
	boolean existsByPublicationIdAndUsername(String publicationId, String username);

	long countByPublicationId(String publicationId);

	List<PublicationRecipient> findByPublicationId(String publicationId);

	void deleteByPublicationId(String publicationId);

	Optional<PublicationRecipient> findByPublicationIdAndUsername(String publicationId, String username);

	/**
	 * 直接在数据库中分页查询快照受众中的未读账号，避免大型学校一次把数千个账号
	 * 序列化到管理端并同时创建数千个表格节点。
	 */
	@Query(value = """
			select recipient.username
			from msg_publication_recipient recipient
			left join msg_publication_read receipt
			  on receipt.publication_id = recipient.publication_id
			 and receipt.username = recipient.username
			where recipient.publication_id = :publicationId
			  and receipt.id is null
			order by recipient.username
			""", countQuery = """
			select count(*)
			from msg_publication_recipient recipient
			left join msg_publication_read receipt
			  on receipt.publication_id = recipient.publication_id
			 and receipt.username = recipient.username
			where recipient.publication_id = :publicationId
			  and receipt.id is null
			""", nativeQuery = true)
	Page<String> findUnreadUsernames(
			@Param("publicationId") String publicationId,
			Pageable pageable);
}
