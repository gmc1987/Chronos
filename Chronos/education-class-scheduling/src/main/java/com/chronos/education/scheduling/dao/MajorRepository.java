package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.Major;

public interface MajorRepository extends JpaRepository<Major, String> {
	List<Major> findAllByOrderByMajorCode();
}
