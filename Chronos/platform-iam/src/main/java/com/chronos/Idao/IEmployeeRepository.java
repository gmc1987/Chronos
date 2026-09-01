package com.chronos.Idao;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.chronos.model.pojo.Employee;
import java.time.LocalDate;
import java.util.List;
public interface IEmployeeRepository extends JpaRepository<Employee,String> { Optional<Employee> findByEmployeeCode(String employeeCode); List<Employee> findByLeaveDateLessThanEqualAndEmploymentStatusNot(LocalDate date,String status); }
