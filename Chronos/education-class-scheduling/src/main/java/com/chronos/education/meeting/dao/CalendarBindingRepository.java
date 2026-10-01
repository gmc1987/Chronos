package com.chronos.education.meeting.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.meeting.model.CalendarBinding;

public interface CalendarBindingRepository extends JpaRepository<CalendarBinding, String> {
	Optional<CalendarBinding> findByUsername(String username);
}
