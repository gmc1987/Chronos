package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamRoom;

public interface ExamRoomRepository extends JpaRepository<ExamRoom, String> {
	List<ExamRoom> findBySessionId(String sessionId);
	boolean existsBySessionIdAndClassroomId(String sessionId, String classroomId);
}
