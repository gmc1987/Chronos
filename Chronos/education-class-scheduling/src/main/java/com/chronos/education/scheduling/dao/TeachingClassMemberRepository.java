package com.chronos.education.scheduling.dao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.chronos.education.scheduling.model.TeachingClassMember;

public interface TeachingClassMemberRepository extends JpaRepository<TeachingClassMember, String> {
	String VISIBLE_MEMBERS = """
			from TeachingClassMember member
			where member.offeringId = :offeringId
			  and (:fullAccess = true or exists (
			      select 1 from StudentProfile student
			      where student.id = member.studentId
			        and (student.id in :studentIds or student.administrativeClassId in :classIds
			             or student.gradeId in :gradeIds)
			  ))
			""";

	@Query("select member " + VISIBLE_MEMBERS + " order by member.createTime, member.id")
	Page<TeachingClassMember> findVisiblePage(@Param("offeringId") String offeringId,
			@Param("fullAccess") boolean fullAccess, @Param("studentIds") List<String> studentIds,
			@Param("classIds") List<String> classIds, @Param("gradeIds") List<String> gradeIds, Pageable pageable);

	@Query("select count(member) " + VISIBLE_MEMBERS + " and member.enrollmentStatus = 'ENROLLED'")
	long countVisibleEnrolled(@Param("offeringId") String offeringId, @Param("fullAccess") boolean fullAccess,
			@Param("studentIds") List<String> studentIds, @Param("classIds") List<String> classIds,
			@Param("gradeIds") List<String> gradeIds);

	void deleteByOfferingId(String offeringId);

	List<TeachingClassMember> findByOfferingIdOrderByCreateTime(String offeringId);
	List<TeachingClassMember> findByOfferingIdInAndEnrollmentStatus(
			List<String> offeringIds,
			String enrollmentStatus);
	List<TeachingClassMember> findByStudentIdAndEnrollmentStatus(String studentId, String enrollmentStatus);
	List<TeachingClassMember> findByStudentIdAndEnrollmentStatusIn(
			String studentId, List<String> enrollmentStatuses);
	List<TeachingClassMember> findByStudentIdInAndEnrollmentStatus(
			List<String> studentIds,
			String enrollmentStatus);
	List<TeachingClassMember> findByOfferingIdAndEnrollmentStatus(String offeringId, String enrollmentStatus);
	Optional<TeachingClassMember> findByOfferingIdAndStudentId(String offeringId, String studentId);
}
