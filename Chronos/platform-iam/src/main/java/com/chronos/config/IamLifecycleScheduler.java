package com.chronos.config;

import java.time.LocalDate;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IRefreshTokenService;

@Configuration
@EnableScheduling
public class IamLifecycleScheduler {
    private final IEmployeeRepository employees;private final IAdminUserRepository users;private final IEmployeeAssignmentRepository assignments;
    private final IOrganizationUnitRepository units;private final IRefreshTokenService tokens;private final IAuditLogService audit;
    public IamLifecycleScheduler(IEmployeeRepository employees,IAdminUserRepository users,IEmployeeAssignmentRepository assignments,IOrganizationUnitRepository units,IRefreshTokenService tokens,IAuditLogService audit){this.employees=employees;this.users=users;this.assignments=assignments;this.units=units;this.tokens=tokens;this.audit=audit;}
    @Scheduled(cron="${chronos.iam.lifecycle-cron:0 5 0 * * *}")
    @Transactional
    public void reconcile(){LocalDate today=LocalDate.now();for(var employee:employees.findByLeaveDateLessThanEqualAndEmploymentStatusNot(today,"LEFT")){employee.setEmploymentStatus("LEFT");employees.save(employee);users.findByEmployeeId(employee.getId()).ifPresent(user->{user.setStatus(0);user.setAccountLocked(true);user.setTokenVersion((user.getTokenVersion()==null?0:user.getTokenVersion())+1);users.save(user);tokens.revokeAll(user.getUsername());audit.log("system","EMPLOYEE_AUTO_LEAVE","employeeId="+employee.getId());});}for(var unit:units.findByLeaderEmployeeIdIsNotNull()){boolean valid=assignments.findCurrentAssignments(unit.getLeaderEmployeeId(),today).stream().anyMatch(a->unit.getId().equals(a.getOrganizationUnitId()));if(!valid){unit.setLeaderEmployeeId(null);units.save(unit);audit.log("system","DEPARTMENT_LEADER_CLEARED","departmentId="+unit.getId());}}}
}
