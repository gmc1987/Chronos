package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.education.scheduling.model.TeachingClassMember;

public interface TeachingClassMemberRepository extends JpaRepository<TeachingClassMember, String> {
	List<TeachingClassMember> findByOfferingIdOrderByCreateTime(String offeringId);
	List<TeachingClassMember> findByOfferingIdInAndEnrollmentStatus(
			List<String> offeringIds,
			String enrollmentStatus);
	List<TeachingClassMember> findByStudentIdAndEnrollmentStatus(String studentId, String enrollmentStatus);
	List<TeachingClassMember> findByStudentIdInAndEnrollmentStatus(
			List<String> studentIds,
			String enrollmentStatus);
	Optional<TeachingClassMember> findByOfferingIdAndStudentId(String offeringId, String studentId);
}
