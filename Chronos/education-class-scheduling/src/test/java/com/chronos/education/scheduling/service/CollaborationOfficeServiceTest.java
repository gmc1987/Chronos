package com.chronos.education.scheduling.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.model.CollaborationFileShare;
import com.chronos.education.scheduling.model.OfficeResource;
import com.chronos.education.scheduling.repository.CollaborationFileShareRepository;
import com.chronos.education.scheduling.repository.OfficeApprovalRequestRepository;
import com.chronos.education.scheduling.repository.OfficeResourceRepository;
import com.chronos.education.scheduling.repository.OfficialDocumentRepository;
import com.chronos.file.service.ManagedFileService;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.workflow.WorkflowService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollaborationOfficeServiceTest {
	private OfficeResourceRepository resources;
	private OfficeApprovalRequestRepository requests;
	private IAdminUserRepository accounts;
	private IEmployeeRepository employees;
	private WorkflowService workflow;
	private CollaborationOfficeService service;

	@BeforeEach
	void setUp() {
		resources = mock(OfficeResourceRepository.class);
		requests = mock(OfficeApprovalRequestRepository.class);
		accounts = mock(IAdminUserRepository.class);
		employees = mock(IEmployeeRepository.class);
		workflow = mock(WorkflowService.class);
		service = new CollaborationOfficeService(
				resources,
				requests,
				mock(OfficialDocumentRepository.class),
				mock(CollaborationFileShareRepository.class),
				accounts,
				employees,
				workflow,
				mock(ManagedFileService.class));

		AdminUser account = new AdminUser();
		account.setEmployeeId("employee-1");
		Employee employee = new Employee();
		employee.setId("employee-1");
		when(accounts.findByUsername("staff")).thenReturn(account);
		when(employees.findById("employee-1")).thenReturn(Optional.of(employee));
	}

	@Test
	void vehicleConflictBlocksSubmissionBeforeWorkflowStarts() {
		OfficeResource vehicle = new OfficeResource();
		vehicle.setId("vehicle-1");
		vehicle.setResourceType("VEHICLE");
		vehicle.setResourceName("公务车一号");
		vehicle.setCapacity(5);
		vehicle.setEnabled(true);
		LocalDateTime start = LocalDateTime.of(2026, 11, 2, 9, 0);
		LocalDateTime end = start.plusHours(3);
		when(resources.findLockedById("vehicle-1")).thenReturn(Optional.of(vehicle));
		when(requests.countConflicts("vehicle-1", start, end)).thenReturn(1L);

		assertThatThrownBy(() -> service.createRequest(
				"staff",
				new CollaborationOfficeService.OfficeRequestCommand(
						"VEHICLE", "vehicle-1", start, end, "校外教研",
						"市教研院", 4, null, null, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("车辆在所选时间段已被占用");

		verify(workflow, never()).startByCode(
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyString(),
				org.mockito.ArgumentMatchers.anyMap(),
				org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void shareAudienceSupportsOwnerUserDepartmentAndAll() {
		CollaborationFileShare share = new CollaborationFileShare();
		share.setOwnerUsername("owner");

		share.setAudienceType("PRIVATE");
		assertThat(service.canReadShare(share, "owner", "dept-1")).isTrue();
		assertThat(service.canReadShare(share, "reader", "dept-1")).isFalse();

		share.setAudienceType("USER");
		share.setAudienceValue("reader, reviewer");
		assertThat(service.canReadShare(share, "reader", "dept-1")).isTrue();
		assertThat(service.canReadShare(share, "other", "dept-1")).isFalse();

		share.setAudienceType("DEPARTMENT");
		share.setAudienceValue("dept-1");
		assertThat(service.canReadShare(share, "reader", "dept-1")).isTrue();
		assertThat(service.canReadShare(share, "reader", "dept-2")).isFalse();

		share.setAudienceType("ALL");
		assertThat(service.canReadShare(share, "anyone", null)).isTrue();
	}

	@Test
	void vehicleResourceRequiresPositiveCapacity() {
		assertThatThrownBy(() -> service.saveResource(
				null,
				new CollaborationOfficeService.ResourceCommand(
						"VEHICLE", "CAR-01", "公务车", "京A00001", 0, null, true)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("车辆座位数必须大于零");
	}
}
