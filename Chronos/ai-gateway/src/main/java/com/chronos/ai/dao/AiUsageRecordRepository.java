package com.chronos.ai.dao;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.ai.model.AiUsageRecord;

public interface AiUsageRecordRepository extends JpaRepository<AiUsageRecord, String> {
	long countByModelIdAndCreateTimeGreaterThanEqual(String modelId, LocalDateTime from);
	long countByUsernameAndCreateTimeGreaterThanEqual(String username, LocalDateTime from);

	@Query("select coalesce(sum(record.totalTokens), 0) from AiUsageRecord record where record.modelId = :modelId and record.createTime >= :from and record.status <> 'REJECTED'")
	Long sumTokensByModel(@Param("modelId") String modelId, @Param("from") LocalDateTime from);

	@Query("select coalesce(sum(record.totalTokens), 0) from AiUsageRecord record where record.username = :username and record.createTime >= :from and record.status <> 'REJECTED'")
	Long sumTokensByUser(@Param("username") String username, @Param("from") LocalDateTime from);

	@Query("select coalesce(sum(record.totalTokens), 0) from AiUsageRecord record where record.createTime >= :from and record.status <> 'REJECTED'")
	Long sumTokensGlobal(@Param("from") LocalDateTime from);

	@Query("select coalesce(sum(record.costAmount), 0) from AiUsageRecord record where record.modelId = :modelId and record.createTime >= :from")
	BigDecimal sumCostByModel(@Param("modelId") String modelId, @Param("from") LocalDateTime from);

	@Query("select coalesce(sum(record.costAmount), 0) from AiUsageRecord record where record.username = :username and record.createTime >= :from")
	BigDecimal sumCostByUser(@Param("username") String username, @Param("from") LocalDateTime from);

	@Query("select coalesce(sum(record.costAmount), 0) from AiUsageRecord record where record.createTime >= :from")
	BigDecimal sumCostGlobal(@Param("from") LocalDateTime from);

	@Query("""
			select record from AiUsageRecord record
			where (:modelId is null or record.modelId = :modelId)
			  and (:username is null or record.username = :username)
			order by record.createTime desc
			""")
	Page<AiUsageRecord> search(
			@Param("modelId") String modelId,
			@Param("username") String username,
			Pageable pageable);

	@Query("select count(record) from AiUsageRecord record where record.createTime >= :from")
	long countSince(@Param("from") LocalDateTime from);

	@Query("select count(record) from AiUsageRecord record where record.createTime >= :from and record.status = 'REJECTED'")
	long countRejectedSince(@Param("from") LocalDateTime from);
}
