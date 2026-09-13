package com.chronos.file.dao;

import com.chronos.file.model.ManagedFile;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManagedFileRepository extends JpaRepository<ManagedFile, String> {
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
