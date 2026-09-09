package com.chronos.message.Idao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import com.chronos.message.model.PublicationDelivery;

public interface IPublicationDeliveryRepository extends JpaRepository<PublicationDelivery, String> {
	long countByStatus(String status);

	long countByChannelAndStatus(String channel, String status);

	Page<PublicationDelivery> findByStatus(String status, Pageable pageable);

	long countByChannelAndStatusAndDeliveredAtAfter(
			String channel,
			String status,
			LocalDateTime deliveredAfter);

	long countByUsernameAndChannelAndStatusAndDeliveredAtAfter(
			String username,
			String channel,
			String status,
			LocalDateTime deliveredAfter);

	Optional<PublicationDelivery> findTopByUsernameAndChannelAndStatusOrderByDeliveredAtDesc(
			String username,
			String channel,
			String status);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<PublicationDelivery> findByStatusAndNextAttemptAtLessThanEqual(
			String status,
			LocalDateTime nextAttemptAt,
			Pageable pageable);

	void deleteByPublicationId(String publicationId);

	Optional<PublicationDelivery> findByPublicationIdAndUsernameAndChannel(
			String publicationId,
			String username,
			String channel);
}
