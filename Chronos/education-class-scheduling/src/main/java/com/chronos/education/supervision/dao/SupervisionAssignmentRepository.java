package com.chronos.education.supervision.dao;
import com.chronos.education.supervision.model.SupervisionAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupervisionAssignmentRepository extends JpaRepository<SupervisionAssignment, String> {
	List<SupervisionAssignment> findBySupervisorIdOrderByCreateTimeDesc(String supervisorId);
	List<SupervisionAssignment> findByTeacherIdInOrderByCreateTimeDesc(List<String> teacherIds);
	List<SupervisionAssignment> findByPlanId(String planId);
	Optional<SupervisionAssignment> findByIdAndSupervisorId(String id, String supervisorId);
}
