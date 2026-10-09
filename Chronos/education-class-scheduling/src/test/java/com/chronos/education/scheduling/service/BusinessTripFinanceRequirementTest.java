package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.dao.BusinessTripConfigurationRepository;
import com.chronos.education.scheduling.dao.BusinessTripRequestRepository;
import com.chronos.education.scheduling.model.BusinessTripConfiguration;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusinessTripFinanceRequirementTest {
	private BusinessTripConfigurationRepository configurations;
	private BusinessTripService service;
	private BusinessTripConfiguration configuration;

	@BeforeEach
	void setUp() {
		BusinessTripRequestRepository trips = mock(BusinessTripRequestRepository.class);
		configurations = mock(BusinessTripConfigurationRepository.class);
		IAdminUserRepository users = mock(IAdminUserRepository.class);
		IEmployeeRepository employees = mock(IEmployeeRepository.class);
		configuration = new BusinessTripConfiguration();
		configuration.setConfigCode("DEFAULT");
		when(configurations.findByConfigCode("DEFAULT")).thenReturn(Optional.of(configuration));
		AdminUser account = new AdminUser();
		account.setEmployeeId("employee-1");
		Employee employee = new Employee();
		employee.setId("employee-1");
		employee.setEmploymentStatus("ACTIVE");
		when(users.findByUsername("staff")).thenReturn(account);
		when(employees.findById("employee-1")).thenReturn(Optional.of(employee));
		service = new BusinessTripService(
				trips,
				configurations,
				users,
				employees,
				mock(WorkflowService.class),
				mock(IAuditLogService.class),
				List.of());
	}

	@Test
	void requiredFinanceBlocksWorkflowWhenNoProviderIsConnected() {
		configuration.setFinanceRequired(true);

		assertThatThrownBy(() -> service.validateWorkflowStart("staff", validForm()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("出差流程已要求对接财务模块，但完整财务模块尚未接入，流程不能继续");
		assertThat(service.integrationStatus())
				.containsEntry("submissionAllowed", false)
				.containsEntry("financeConnected", false);
	}

	@Test
	void disabledFinanceRequirementAllowsWorkflowWithoutProvider() {
		configuration.setFinanceRequired(false);

		service.validateWorkflowStart("staff", validForm());

		assertThat(service.integrationStatus())
				.containsEntry("submissionAllowed", true)
				.containsEntry("financeConnected", false);
	}

	private Map<String, Object> validForm() {
		return Map.of(
				"destination", "上海",
				"startDate", "2026-10-20",
				"endDate", "2026-10-22",
				"purpose", "参加教学研讨会",
				"currency", "CNY",
				"transportAmount", "1000",
				"accommodationAmount", "1200",
				"mealAmount", "300",
				"otherAmount", "0");
	}
}
