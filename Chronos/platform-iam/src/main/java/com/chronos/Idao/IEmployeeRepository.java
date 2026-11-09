package com.chronos.Idao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.Employee;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface IEmployeeRepository extends JpaRepository<Employee,String> {
    Optional<Employee> findByEmployeeCode(String employeeCode);
    List<Employee> findByLeaveDateLessThanEqualAndEmploymentStatusNot(LocalDate date,String status);
    Page<Employee> findByEmployeeCodeContainingIgnoreCaseOrEmployeeNameContainingIgnoreCaseOrPhoneContainingIgnoreCase(
            String employeeCode, String employeeName, String phone, Pageable pageable);
    @Query(value = "select distinct e from Employee e where exists (select 1 from EmployeeAssignment a where a.employeeId=e.id and a.organizationId=:organizationId and a.status=1) and (:keyword='' or lower(e.employeeCode) like lower(concat('%',:keyword,'%')) or lower(e.employeeName) like lower(concat('%',:keyword,'%')) or lower(coalesce(e.phone,'')) like lower(concat('%',:keyword,'%')))",
            countQuery = "select count(e) from Employee e where exists (select 1 from EmployeeAssignment a where a.employeeId=e.id and a.organizationId=:organizationId and a.status=1) and (:keyword='' or lower(e.employeeCode) like lower(concat('%',:keyword,'%')) or lower(e.employeeName) like lower(concat('%',:keyword,'%')) or lower(coalesce(e.phone,'')) like lower(concat('%',:keyword,'%')))" )
    Page<Employee> findVisibleByOrganization(@Param("organizationId") String organizationId,
            @Param("keyword") String keyword, Pageable pageable);
    @Query("select distinct e from Employee e where exists (select 1 from EmployeeAssignment a where a.employeeId=e.id and a.organizationId=:organizationId and a.status=1)")
    List<Employee> findVisibleByOrganization(@Param("organizationId") String organizationId);
}
