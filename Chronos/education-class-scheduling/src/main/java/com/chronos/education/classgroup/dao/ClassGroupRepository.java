package com.chronos.education.classgroup.dao;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.classgroup.model.ClassGroup;

public interface ClassGroupRepository extends JpaRepository<ClassGroup, String> {
	Optional<ClassGroup> findByClassId(String classId);
	Page<ClassGroup> findAllByOrderByCreateTimeDesc(Pageable pageable);
}
