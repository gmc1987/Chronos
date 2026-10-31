package com.chronos.education.internship.dao;

import com.chronos.education.internship.model.InternshipActivity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InternshipActivityRepository extends JpaRepository<InternshipActivity, String> {
	List<InternshipActivity> findByPlacementIdOrderByActivityDateDesc(String placementId);
}
