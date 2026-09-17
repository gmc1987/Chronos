package com.chronos.education.meeting.dao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.education.meeting.model.Meeting;

public interface MeetingRepository extends JpaRepository<Meeting, String> {
	List<Meeting> findAllByOrderByStartTimeDesc();

	List<Meeting> findByOrganizerUsernameOrderByStartTimeDesc(String username);

	boolean existsByRoomId(String roomId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update Meeting meeting
			set meeting.status = 'COMPLETED',
				meeting.lastUpdateBy = 'SYSTEM',
				meeting.lastUpdateTime = :now
			where meeting.status = 'PUBLISHED'
			  and meeting.endTime < :now
			""")
	int completeExpired(@Param("now") LocalDateTime now);

	@Query("""
			select meeting from Meeting meeting
			where meeting.roomId = :roomId
			  and meeting.id <> :excludedId
			  and meeting.status in :statuses
			  and meeting.startTime < :endTime
			  and meeting.endTime > :startTime
			""")
	List<Meeting> findRoomConflicts(
			@Param("roomId") String roomId,
			@Param("excludedId") String excludedId,
			@Param("startTime") LocalDateTime startTime,
			@Param("endTime") LocalDateTime endTime,
			@Param("statuses") Collection<String> statuses);
}
