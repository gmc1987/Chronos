package com.chronos.education.meeting.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.meeting.model.MeetingSeries;

public interface MeetingSeriesRepository extends JpaRepository<MeetingSeries, String> {
}
