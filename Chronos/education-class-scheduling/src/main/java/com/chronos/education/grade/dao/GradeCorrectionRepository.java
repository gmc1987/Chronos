package com.chronos.education.grade.dao;

import com.chronos.education.grade.model.GradeCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeCorrectionRepository extends JpaRepository<GradeCorrection, String> {
}
