package com.chronos.education.academic.dao;

import com.chronos.education.academic.model.AcademicProgram;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AcademicProgramRepository extends JpaRepository<AcademicProgram, String> {
	List<AcademicProgram> findBySchoolIdOrderByCohortYearDescProgramCode(String schoolId);
	Optional<AcademicProgram> findFirstBySchoolIdAndMajorIdAndCohortYearAndEducationLevelAndStatus(
			String schoolId, String majorId, Integer cohortYear, String educationLevel, String status);
	boolean existsBySchoolIdAndProgramCodeAndVersionNo(String schoolId, String programCode, Integer versionNo);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select program from AcademicProgram program where program.id = :id")
	Optional<AcademicProgram> findForUpdate(@Param("id") String id);
}
