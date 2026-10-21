package com.chronos.education.scheduling.dao;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.Major;
import java.util.Optional;

public interface MajorRepository extends JpaRepository<Major, String> {
	Optional<Major> findByMajorCode(String majorCode);
	List<Major> findAllByOrderByMajorCode();
	Page<Major> findAllByOrderByMajorCode(Pageable pageable);
}
