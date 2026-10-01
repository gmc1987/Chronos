package com.chronos.education.meeting.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.meeting.model.CalendarOutbox;

public interface CalendarOutboxRepository extends JpaRepository<CalendarOutbox, String> {
	Optional<CalendarOutbox> findByIdempotencyKey(String idempotencyKey);
}
