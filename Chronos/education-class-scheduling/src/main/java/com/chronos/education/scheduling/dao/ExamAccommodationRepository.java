package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.ExamAccommodation;

public interface ExamAccommodationRepository extends JpaRepository<ExamAccommodation, String> {
	List<ExamAccommodation> findByRegistrationIdOrderByCreateTimeDesc(String registrationId);
}
