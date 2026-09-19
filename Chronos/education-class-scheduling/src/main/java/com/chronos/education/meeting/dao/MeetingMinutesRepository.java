package com.chronos.education.meeting.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.meeting.model.MeetingMinutes;

public interface MeetingMinutesRepository extends JpaRepository<MeetingMinutes, String> {
	Optional<MeetingMinutes> findByMeetingId(String meetingId);
}
