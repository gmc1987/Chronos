package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.ExamInvigilation;

public interface ExamInvigilationRepository extends JpaRepository<ExamInvigilation, String> {
	List<ExamInvigilation> findByRoomIdAndStatus(String roomId, String status);
	List<ExamInvigilation> findByTeacherIdAndStatus(String teacherId, String status);
	List<ExamInvigilation> findByTeacherIdAndStatusIn(
			String teacherId,
			List<String> statuses);
	List<ExamInvigilation> findByStatusAndCheckedInAtIsNullAndAbsenceEscalatedAtIsNull(
			String status);
}
