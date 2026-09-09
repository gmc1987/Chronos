package com.chronos.message.Idao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

import jakarta.persistence.LockModeType;

import com.chronos.message.model.Publication;

public interface IPublicationRepository
		extends JpaRepository<Publication, String>, JpaSpecificationExecutor<Publication> {
	List<Publication> findByStatusIn(List<String> statuses);

	List<Publication> findByStatusAndArchivedFalseAndExpireAtBefore(
			String status,
			LocalDateTime expireAt);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select publication from Publication publication where publication.status in :statuses")
	List<Publication> findLifecycleCandidates(@Param("statuses") List<String> statuses);

	List<Publication> findByApprovalStatusAndApprovalInstanceIdIsNotNull(String approvalStatus);

	@Query("""
			select publication
			from Publication publication
			where publication.status = 'PUBLISHED'
			  and publication.archived = false
			  and (:type is null or publication.publicationType = :type)
			  and (:keyword is null
			       or lower(publication.title) like lower(concat('%', :keyword, '%'))
			       or lower(publication.summary) like lower(concat('%', :keyword, '%')))
			  and (publication.publishedAt is null or publication.publishedAt <= :now)
			  and (publication.expireAt is null or publication.expireAt > :now)
			  and exists (
			      select recipient.id
			      from PublicationRecipient recipient
			      where recipient.publicationId = publication.id
			        and recipient.username = :username
			  )
			  and (:unreadOnly = false or not exists (
			      select receipt.id
			      from PublicationRead receipt
			      where receipt.publicationId = publication.id
			        and receipt.username = :username
			  ))
			""")
	Page<Publication> findVisibleForUser(
			@Param("username") String username,
			@Param("type") String type,
			@Param("keyword") String keyword,
			@Param("unreadOnly") boolean unreadOnly,
			@Param("now") LocalDateTime now,
			Pageable pageable);

	/**
	 * 无关键字查询必须使用独立 JPQL。PostgreSQL 无法为 nullable 参数稳定推断字符串类型，
	 * 将 null 传入 lower/concat 会被绑定成 bytea 并触发 lower(bytea) 错误。
	 */
	@Query("""
			select publication
			from Publication publication
			where publication.status = 'PUBLISHED'
			  and publication.archived = false
			  and (:type is null or publication.publicationType = :type)
			  and (publication.publishedAt is null or publication.publishedAt <= :now)
			  and (publication.expireAt is null or publication.expireAt > :now)
			  and exists (
			      select recipient.id
			      from PublicationRecipient recipient
			      where recipient.publicationId = publication.id
			        and recipient.username = :username
			  )
			  and (:unreadOnly = false or not exists (
			      select receipt.id
			      from PublicationRead receipt
			      where receipt.publicationId = publication.id
			        and receipt.username = :username
			  ))
			""")
	Page<Publication> findVisibleForUserWithoutKeyword(
			@Param("username") String username,
			@Param("type") String type,
			@Param("unreadOnly") boolean unreadOnly,
			@Param("now") LocalDateTime now,
			Pageable pageable);
}
