package com.chronos.education.meeting.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.meeting.model.MeetingActionItem;

public interface MeetingActionItemRepository extends JpaRepository<MeetingActionItem, String> {
	List<MeetingActionItem> findByMeetingIdOrderByCreateTimeAsc(String meetingId);
}
