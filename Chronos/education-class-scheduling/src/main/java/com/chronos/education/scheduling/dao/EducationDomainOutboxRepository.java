package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.EducationDomainOutbox;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EducationDomainOutboxRepository extends JpaRepository<EducationDomainOutbox, String> {
	Optional<EducationDomainOutbox> findByEventId(String eventId);
	Page<EducationDomainOutbox> findByStatusOrderByCreateTimeDesc(String status, Pageable pageable);

	/** 多实例调度器通过跳过已锁记录来分片领取事件，防止重复并发消费。 */
	@Query(value = """
			select * from edu_domain_outbox
			where status = 'PENDING' and next_attempt_at <= :now
			order by create_time asc
			for update skip locked
			limit 100
			""", nativeQuery = true)
	List<EducationDomainOutbox> lockDispatchBatch(@Param("now") LocalDateTime now);
}
