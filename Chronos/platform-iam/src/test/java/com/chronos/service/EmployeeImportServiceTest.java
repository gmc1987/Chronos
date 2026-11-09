package com.chronos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPersonExternalIdentityRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.Organization;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.pojo.PersonExternalIdentity;
import com.chronos.model.pojo.Position;
import com.chronos.service.impl.BusinessNumberService;
import com.chronos.service.impl.EmployeeImportService;
import com.chronos.service.impl.StaffAccountProvisioningService;

class EmployeeImportServiceTest {
	private IEmployeeRepository employees;
	private IPersonExternalIdentityRepository identities;
	private BusinessNumberService businessNumbers;
	private StaffAccountProvisioningService accounts;
	private EmployeeImportService service;
	private Organization organization;
	private OrganizationUnit department;
	private Position position;

	@BeforeEach
	void setUp() {
		IOrganizationRepository organizations = mock(IOrganizationRepository.class);
		IOrganizationUnitRepository units = mock(IOrganizationUnitRepository.class);
		IPositionRepository positions = mock(IPositionRepository.class);
		IJobLevelRepository levels = mock(IJobLevelRepository.class);
		employees = mock(IEmployeeRepository.class);
		IEmployeeAssignmentRepository assignments = mock(IEmployeeAssignmentRepository.class);
		identities = mock(IPersonExternalIdentityRepository.class);
		businessNumbers = mock(BusinessNumberService.class);
		accounts = mock(StaffAccountProvisioningService.class);
		service = new EmployeeImportService(organizations, units, positions, levels, employees,
				assignments, identities, businessNumbers, accounts);

		organization = new Organization();
		organization.setId("org-1");
		organization.setOrgCode("SCHOOL_001");
		department = new OrganizationUnit();
		department.setId("department-1");
		department.setOrgId("org-1");
		position = new Position();
		position.setId("position-1");
		position.setPositionCode("LECTURER");
		when(organizations.findByOrgCode("SCHOOL_001")).thenReturn(organization);
		when(units.findByOrgIdAndOrganizationUnitCode("org-1", "COLLEGE_CS"))
				.thenReturn(Optional.of(department));
        when(positions.findByOrganizationIdAndPositionCode("org-1", "LECTURER"))
				.thenReturn(Optional.of(position));
		when(assignments.findFirstByEmployeeIdAndOrganizationIdAndOrganizationUnitIdAndPositionId(
				any(), any(), any(), any())).thenReturn(Optional.empty());
		when(assignments.save(any(EmployeeAssignment.class))).thenAnswer(invocation -> {
			EmployeeAssignment value = invocation.getArgument(0);
			if (value.getId() == null) value.setId("assignment-1");
			return value;
		});
		when(employees.save(any(Employee.class))).thenAnswer(invocation -> {
			Employee value = invocation.getArgument(0);
			if (value.getId() == null) value.setId("employee-1");
			return value;
		});
		when(identities.save(any(PersonExternalIdentity.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(accounts.sync(any(), any(Boolean.class), any(Boolean.class))).thenReturn(
				new StaffAccountProvisioningService.AccountSyncResult(
						"202600001", "王老师", "202600001", true, "Abc234567890",
						Set.of("ROLE_PLATFORM_USER"), List.of()));
	}

	@Test
	void shouldGenerateEmployeeNumberAndCreateAccountForNewExternalIdentity() {
		when(identities.findByOrganizationIdAndPersonTypeAndSourceSystemAndExternalPersonKey(
				"org-1", "EMPLOYEE", "EXCEL", "HR-001")).thenReturn(Optional.empty());
		when(businessNumbers.nextEmployeeNumber("org-1", java.time.LocalDate.of(2026, 9, 1)))
				.thenReturn("202600001");
		when(employees.findByEmployeeCode("202600001")).thenReturn(Optional.empty());

		Map<String, Object> result = service.importRows(List.of(employeeRow("HR-001")),
				new EmployeeImportService.ImportOptions(true, true, "EXCEL", false));

		assertThat(((Map<?, ?>) result.get("employees")).get("created")).isEqualTo(1);
		assertThat(((Map<?, ?>) result.get("accounts")).get("created")).isEqualTo(1L);
		verify(businessNumbers).nextEmployeeNumber("org-1", java.time.LocalDate.of(2026, 9, 1));
		verify(identities).save(any(PersonExternalIdentity.class));
		verify(accounts).sync(any(Employee.class), org.mockito.ArgumentMatchers.eq(true),
				org.mockito.ArgumentMatchers.eq(false));
	}

	@Test
	void shouldUpdateByExternalIdentityWithoutAllocatingAnotherNumber() {
		Employee existing = new Employee();
		existing.setId("employee-1");
		existing.setEmployeeCode("202600001");
		PersonExternalIdentity identity = new PersonExternalIdentity();
		identity.setPersonId(existing.getId());
		when(identities.findByOrganizationIdAndPersonTypeAndSourceSystemAndExternalPersonKey(
				"org-1", "EMPLOYEE", "EXCEL", "HR-001")).thenReturn(Optional.of(identity));
		when(employees.findById("employee-1")).thenReturn(Optional.of(existing));

		Map<String, Object> result = service.importRows(List.of(employeeRow("HR-001")),
				new EmployeeImportService.ImportOptions(false, true, "EXCEL", false));

		assertThat(((Map<?, ?>) result.get("employees")).get("updated")).isEqualTo(1);
		assertThat(existing.getEmployeeCode()).isEqualTo("202600001");
		verify(businessNumbers, never()).nextEmployeeNumber(any(), any());
		verify(accounts, never()).sync(any(), any(Boolean.class), any(Boolean.class));
	}

	private Map<String, String> employeeRow(String externalKey) {
		return Map.ofEntries(
				Map.entry("导入标识*", externalKey),
				Map.entry("工号", ""),
				Map.entry("姓名*", "王老师"),
				Map.entry("性别", "FEMALE"),
				Map.entry("手机号", "13800000000"),
				Map.entry("邮箱", "teacher@example.com"),
				Map.entry("员工类型", "STAFF"),
				Map.entry("在职状态", "ACTIVE"),
				Map.entry("入职日期", "2026-09-01"),
				Map.entry("离职日期", ""),
				Map.entry("机构编码", "SCHOOL_001"),
				Map.entry("部门编码", "COLLEGE_CS"),
				Map.entry("岗位编码", "LECTURER"),
				Map.entry("职级编码", ""),
				Map.entry("主任职", "是"),
				Map.entry("部门负责人", "否"),
				Map.entry("任职生效日期", "2026-09-01"),
				Map.entry("任职失效日期", ""),
				Map.entry("任职状态", "1"));
	}
}
