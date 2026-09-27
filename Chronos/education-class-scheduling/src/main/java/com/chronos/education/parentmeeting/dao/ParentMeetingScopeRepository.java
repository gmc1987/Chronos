package com.chronos.education.parentmeeting.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.parentmeeting.model.ParentMeetingScope;

public interface ParentMeetingScopeRepository extends JpaRepository<ParentMeetingScope, String> {
	List<ParentMeetingScope> findByMeetingId(String meetingId);
}
