package com.chronos.file.dao;

import com.chronos.file.model.ManagedFile;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ManagedFileRepository extends JpaRepository<ManagedFile, String> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select file from ManagedFile file where file.id = :id")
	Optional<ManagedFile> findForBinding(String id);

	List<ManagedFile> findByBusinessTypeAndBusinessIdAndStatusOrderByCreateTime(
			String businessType, String businessId, String status);

	List<ManagedFile> findTop100ByBusinessTypeAndBusinessIdIsNullAndStatusAndCreateTimeBeforeOrderByCreateTimeAsc(
			String businessType,
			String status,
			LocalDateTime createTime);

	List<ManagedFile> findTop100ByBindStateAndStatusAndExpiresAtBeforeOrderByCreateTimeAsc(
			String bindState, String status, LocalDateTime expiresAt);

	List<ManagedFile> findByBindStateAndBusinessIdIsNotNullAndStatus(
			String bindState, String status);
}
