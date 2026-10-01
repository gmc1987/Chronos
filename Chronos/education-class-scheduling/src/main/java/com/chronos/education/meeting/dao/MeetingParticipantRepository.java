package com.chronos.education.meeting.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.chronos.education.meeting.model.MeetingParticipant;

public interface MeetingParticipantRepository extends JpaRepository<MeetingParticipant, String> {
	List<MeetingParticipant> findByMeetingIdOrderByCreateTimeAsc(String meetingId);

	Optional<MeetingParticipant> findByMeetingIdAndUsername(String meetingId, String username);

	void deleteByMeetingId(String meetingId);

	long countByMeetingId(String meetingId);

	@Query("select participant.meetingId from MeetingParticipant participant where participant.username = :username")
	List<String> findMeetingIdsByUsername(@Param("username") String username);
}
