package com.chronos.education.scheduling.dao;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.chronos.education.scheduling.model.Classroom;

public interface ClassroomRepository extends JpaRepository<Classroom, String> {
	List<Classroom> findByEnabledTrueOrderByRoomCode();
	Page<Classroom> findByEnabledTrueOrderByRoomCode(Pageable pageable);
}
