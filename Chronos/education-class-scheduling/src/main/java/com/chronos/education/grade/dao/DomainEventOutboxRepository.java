package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.DomainEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface DomainEventOutboxRepository extends JpaRepository<DomainEventOutbox, String> {
	boolean existsByDeduplicationKey(String deduplicationKey);

	Page<DomainEventOutbox> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);

	@Query("""
			select event from DomainEventOutbox event
			where (event.status = 'PENDING' and event.nextAttemptAt <= :now)
			   or (event.status = 'PROCESSING' and event.leaseUntil < :now)
			order by event.createTime
			""")
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<DomainEventOutbox> findDispatchCandidates(@Param("now") LocalDateTime now, Pageable pageable);

	List<DomainEventOutbox> findByStatusOrderByCreateTimeAsc(String status, Pageable pageable);

	List<DomainEventOutbox> findAllByOrderByCreateTimeDesc(Pageable pageable);

	long countByStatus(String status);
}
