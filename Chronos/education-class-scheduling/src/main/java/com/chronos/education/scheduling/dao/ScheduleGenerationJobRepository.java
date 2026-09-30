package com.chronos.education.scheduling.dao;

import com.chronos.education.scheduling.model.ScheduleGenerationJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleGenerationJobRepository extends JpaRepository<ScheduleGenerationJob, String> {
	List<ScheduleGenerationJob> findTop20BySemesterCodeOrderByCreateTimeDesc(String semesterCode);
	List<ScheduleGenerationJob> findByStatusIn(List<String> statuses);

	Optional<ScheduleGenerationJob> findByAgentRunId(String agentRunId);

	@Query(value = "SELECT CAST(clock_timestamp() AS timestamp)", nativeQuery = true)
	LocalDateTime databaseTime();

	List<ScheduleGenerationJob> findByStatusInAndLeaseExpiresAtLessThanEqual(
			List<String> statuses, LocalDateTime expiredAt);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.status = 'RUNNING', j.leaseOwner = :owner, "
			+ "j.leaseExpiresAt = :expires, j.startedAt = :now, j.progress = 10, "
			+ "j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status = 'QUEUED' and j.leaseOwner is null "
			+ "and j.leaseExpiresAt > :now")
	int claim(@Param("id") String id, @Param("owner") String owner,
			@Param("now") LocalDateTime now, @Param("expires") LocalDateTime expires);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.leaseExpiresAt = :expires, "
			+ "j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status = 'RUNNING' and j.leaseOwner = :owner "
			+ "and j.leaseExpiresAt > :now")
	int heartbeat(@Param("id") String id, @Param("owner") String owner,
			@Param("now") LocalDateTime now, @Param("expires") LocalDateTime expires);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.progress = :progress, "
			+ "j.leaseExpiresAt = :expires, j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status = 'RUNNING' and j.leaseOwner = :owner "
			+ "and j.leaseExpiresAt > :now")
	int progress(@Param("id") String id, @Param("owner") String owner,
			@Param("progress") int progress, @Param("now") LocalDateTime now,
			@Param("expires") LocalDateTime expires);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.resultCandidateIds = :resultIds, "
			+ "j.leaseExpiresAt = :expires, j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status = 'RUNNING' and j.leaseOwner = :owner "
			+ "and j.leaseExpiresAt > :now")
	int recordCommittedCandidates(@Param("id") String id, @Param("owner") String owner,
			@Param("resultIds") String resultIds, @Param("now") LocalDateTime now,
			@Param("expires") LocalDateTime expires);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.status = 'SUCCEEDED', j.progress = 100, "
			+ "j.resultCandidateIds = :resultIds, j.finishedAt = :now, "
			+ "j.leaseOwner = null, j.leaseExpiresAt = null, j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status = 'RUNNING' and j.leaseOwner = :owner "
			+ "and j.leaseExpiresAt > :now")
	int succeed(@Param("id") String id, @Param("owner") String owner,
			@Param("resultIds") String resultIds, @Param("now") LocalDateTime now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.status = 'FAILED', j.progress = 100, "
			+ "j.errorMessage = :error, j.finishedAt = :now, "
			+ "j.leaseOwner = null, j.leaseExpiresAt = null, j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status = 'RUNNING' and j.leaseOwner = :owner "
			+ "and j.leaseExpiresAt > :now")
	int failOwned(@Param("id") String id, @Param("owner") String owner,
			@Param("error") String error, @Param("now") LocalDateTime now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.status = 'FAILED', "
			+ "j.errorMessage = :error, j.finishedAt = :now, "
			+ "j.leaseOwner = null, j.leaseExpiresAt = null, j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status in ('QUEUED', 'RUNNING') "
			+ "and j.leaseExpiresAt <= :now")
	int failExpired(@Param("id") String id, @Param("error") String error,
			@Param("now") LocalDateTime now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.status = 'FAILED', "
			+ "j.errorMessage = :error, j.finishedAt = :now, "
			+ "j.leaseExpiresAt = null, j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status = 'QUEUED' and j.leaseOwner is null "
			+ "and j.leaseExpiresAt > :now")
	int failQueued(@Param("id") String id, @Param("error") String error,
			@Param("now") LocalDateTime now);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update ScheduleGenerationJob j set j.status = 'CANCELLED', "
			+ "j.errorMessage = :reason, j.finishedAt = :now, "
			+ "j.leaseOwner = null, j.leaseExpiresAt = null, j.lockVersion = j.lockVersion + 1 "
			+ "where j.id = :id and j.status in ('QUEUED', 'RUNNING')")
	int cancelActive(@Param("id") String id, @Param("reason") String reason,
			@Param("now") LocalDateTime now);
}
