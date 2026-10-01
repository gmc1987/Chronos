package com.chronos.education.meeting.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.meeting.model.MeetingRoom;

public interface MeetingRoomRepository extends JpaRepository<MeetingRoom, String> {
	List<MeetingRoom> findAllByOrderByRoomNameAsc();

	boolean existsByRoomCodeAndIdNot(String roomCode, String id);

	boolean existsByRoomCode(String roomCode);
}
