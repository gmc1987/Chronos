package com.chronos.education.meeting.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.meeting.model.MeetingMaterial;

public interface MeetingMaterialRepository extends JpaRepository<MeetingMaterial, String> {
	List<MeetingMaterial> findByMeetingIdOrderByCreateTimeAsc(String meetingId);

	Optional<MeetingMaterial> findByFileId(String fileId);
}
