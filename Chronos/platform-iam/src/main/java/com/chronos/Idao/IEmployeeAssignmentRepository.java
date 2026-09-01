package com.chronos.Idao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.EmployeeAssignment;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface IEmployeeAssignmentRepository extends JpaRepository<EmployeeAssignment,String> {
    List<EmployeeAssignment> findByEmployeeIdAndStatus(String employeeId,Integer status);
    Optional<EmployeeAssignment> findFirstByEmployeeIdAndPrimaryAssignmentTrueAndStatus(String employeeId,Integer status);
    Optional<EmployeeAssignment> findFirstByEmployeeIdAndOrganizationIdAndOrganizationUnitIdAndPositionId(String employeeId,String organizationId,String organizationUnitId,String positionId);
    List<EmployeeAssignment> findByOrganizationUnitIdAndStatus(String organizationUnitId,Integer status);
    List<EmployeeAssignment> findByOrganizationUnitIdAndDepartmentLeaderTrueAndStatus(String organizationUnitId,Integer status);
    List<EmployeeAssignment> findByOrganizationUnitId(String organizationUnitId);
    boolean existsByPositionIdAndStatus(String positionId,Integer status);
    boolean existsByJobLevelIdAndStatus(String jobLevelId,Integer status);
    long countByOrganizationId(String organizationId);
    long countByOrganizationUnitId(String organizationUnitId);
    @Query("select a from EmployeeAssignment a where a.employeeId=:employeeId and a.primaryAssignment=true and a.status=1 and (a.effectiveFrom is null or a.effectiveFrom<=:date) and (a.effectiveTo is null or a.effectiveTo>=:date)")
    Optional<EmployeeAssignment> findCurrentPrimaryAssignment(@Param("employeeId") String employeeId,@Param("date") LocalDate date);
    @Query("select a from EmployeeAssignment a where a.employeeId=:employeeId and a.status=1 and (a.effectiveFrom is null or a.effectiveFrom<=:date) and (a.effectiveTo is null or a.effectiveTo>=:date)")
    List<EmployeeAssignment> findCurrentAssignments(@Param("employeeId") String employeeId,@Param("date") LocalDate date);
}
