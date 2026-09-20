package com.chronos.integration.dao;
import com.chronos.integration.model.SyncJob;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SyncJobRepository extends JpaRepository<SyncJob,String> {
 List<SyncJob> findByStatus(String status);
 @Modifying
 @Transactional
 @Query("update SyncJob j set j.leaseOwner = :owner, j.leaseUntil = :leaseUntil where j.id = :id and (j.leaseUntil is null or j.leaseUntil <= :now)")
 int claimLease(String id, String owner, LocalDateTime now, LocalDateTime leaseUntil);
}
