package com.chronos.service.iService;
import java.util.List;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.JobTitle;
import com.chronos.model.pojo.JobLevel;
import com.chronos.model.dto.OrganizationUnitDTO;
import com.chronos.model.dto.EmployeeAssignmentDTO;
import com.chronos.model.vo.OrganizationUnitVO;
import com.chronos.model.vo.EmployeeAssignmentVO;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.pojo.Position;
public interface IIamDirectoryService {
    List<OrganizationUnitVO> organizationUnits(String organizationId);
    OrganizationUnitVO saveOrganizationUnit(OrganizationUnitDTO value);
    void deleteOrganizationUnit(String id);
    List<Position> positions(); Position savePosition(Position value); void deletePosition(String id);
    List<JobTitle> jobTitles(); JobTitle saveJobTitle(JobTitle value); void deleteJobTitle(String id);
    List<JobLevel> jobLevels(); JobLevel saveJobLevel(JobLevel value); void deleteJobLevel(String id);
    List<Employee> employees(); Employee saveEmployee(Employee value); void deleteEmployee(String id);
    List<EmployeeAssignmentVO> assignments(String employeeId);
    EmployeeAssignmentVO saveAssignment(EmployeeAssignmentDTO value); void deleteAssignment(String id);
}
