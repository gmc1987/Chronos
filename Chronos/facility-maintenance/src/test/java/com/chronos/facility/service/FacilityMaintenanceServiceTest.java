package com.chronos.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.facility.dao.MaintenanceLedgerRepository;
import com.chronos.facility.dao.MaintenanceMaterialRepository;
import com.chronos.facility.dao.MaintenanceTechnicianRepository;
import com.chronos.facility.dao.MaterialRequestItemRepository;
import com.chronos.facility.dao.MaterialRequestRepository;
import com.chronos.facility.dao.RepairOrderEventRepository;
import com.chronos.facility.dao.RepairOrderRepository;
import com.chronos.facility.model.MaintenanceTechnician;
import com.chronos.facility.model.RepairOrder;
import com.chronos.facility.model.dto.FacilityDtos.CreateRepairOrder;
import com.chronos.facility.model.dto.FacilityDtos.TechnicianCommand;
import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.service.ManagedFileService;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.Role;
import com.chronos.service.iService.IAuditLogService;

class FacilityMaintenanceServiceTest {
	private final RepairOrderRepository orders = mock(RepairOrderRepository.class);
	private final MaintenanceTechnicianRepository technicians = mock(MaintenanceTechnicianRepository.class);
	private final MaintenanceMaterialRepository materials = mock(MaintenanceMaterialRepository.class);
	private final MaterialRequestRepository requests = mock(MaterialRequestRepository.class);
	private final MaterialRequestItemRepository requestItems = mock(MaterialRequestItemRepository.class);
	private final MaintenanceLedgerRepository ledger = mock(MaintenanceLedgerRepository.class);
	private final RepairOrderEventRepository events = mock(RepairOrderEventRepository.class);
	private final IEmployeeRepository employees = mock(IEmployeeRepository.class);
	private final IOrganizationRepository organizations = mock(IOrganizationRepository.class);
	private final IAdminUserRepository users = mock(IAdminUserRepository.class);
	private final IRoleRepository roles = mock(IRoleRepository.class);
	private final ManagedFileRepository files = mock(ManagedFileRepository.class);
	private final ManagedFileService fileService = mock(ManagedFileService.class);
	private final IAuditLogService audit = mock(IAuditLogService.class);
	private FacilityMaintenanceService service;

	@BeforeEach
	void setUp() {
		service = new FacilityMaintenanceService(orders, technicians, materials, requests, requestItems,
				ledger, events, employees, organizations, users, roles, files, fileService, audit);
		when(orders.nextOrderSequence()).thenReturn(12L);
		when(orders.save(any(RepairOrder.class))).thenAnswer(invocation -> {
			RepairOrder value = invocation.getArgument(0);
			if (value.getId() == null) value.setId("repair-1");
			return value;
		});
		when(files.findByBusinessTypeAndBusinessIdAndStatusOrderByCreateTime(
				any(), any(), any())).thenReturn(List.of());
		when(events.findByRepairOrderIdOrderByCreateTime(any())).thenReturn(List.of());
		when(requests.findByRepairOrderIdOrderByCreateTime(any())).thenReturn(List.of());

		AdminUser reporter = new AdminUser();
		reporter.setUsername("reporter");
		reporter.setDisplayName("报修用户");
		reporter.setStatus(1);
		when(users.findByUsername("reporter")).thenReturn(reporter);
	}

	@Test
	void autoDispatchesToTechnicianWithMatchingSkillAndCapacity() {
		MaintenanceTechnician electrician = new MaintenanceTechnician();
		electrician.setId("technician-1");
		electrician.setUsername("electrician");
		electrician.setTechnicianName("电工一号");
		electrician.setSkillCategories("ELECTRICAL, HVAC");
		electrician.setCurrentLoad(1);
		electrician.setMaxConcurrent(5);
		when(technicians.lockAutoDispatchCandidates()).thenReturn(List.of(electrician));

		var result = service.create(command(List.of("photo-1")), "reporter");

		assertThat(result.status()).isEqualTo("DISPATCHED");
		assertThat(result.technicianUsername()).isEqualTo("electrician");
		assertThat(result.dispatchDeadline()).isAfter(result.dispatchedAt());
		assertThat(result.dispatchAttempts()).isEqualTo(1);
		assertThat(electrician.getCurrentLoad()).isEqualTo(2);
		verify(fileService).bind(List.of("photo-1"), FacilityMaintenanceService.REPORT_FILE_TYPE,
				"repair-1", "reporter");
		verify(technicians).save(electrician);

		ArgumentCaptor<RepairOrder> saved = ArgumentCaptor.forClass(RepairOrder.class);
		verify(orders, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
		assertThat(saved.getValue().getOrderNo()).startsWith("BX").endsWith("000012");
	}

	@Test
	void keepsOrderPendingWhenTechnicianDoesNotCoverRepairArea() {
		MaintenanceTechnician electrician = new MaintenanceTechnician();
		electrician.setId("technician-1");
		electrician.setUsername("electrician");
		electrician.setTechnicianName("电工一号");
		electrician.setSkillCategories("ELECTRICAL");
		electrician.setResponsibilityAreas("实训楼");
		electrician.setCurrentLoad(0);
		electrician.setMaxConcurrent(5);
		when(technicians.lockAutoDispatchCandidates()).thenReturn(List.of(electrician));

		var result = service.create(command(List.of("photo-1")), "reporter");

		assertThat(result.status()).isEqualTo("PENDING_DISPATCH");
		assertThat(result.technicianId()).isNull();
		assertThat(result.dispatchAttempts()).isEqualTo(1);
		verify(technicians, never()).save(electrician);
	}

	@Test
	void reassignsExpiredDispatchToAnotherAvailableTechnician() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 10, 0);
		MaintenanceTechnician previous = technician("technician-old", "old-tech", 1);
		MaintenanceTechnician replacement = technician("technician-new", "new-tech", 0);
		RepairOrder order = new RepairOrder();
		order.setId("repair-timeout");
		order.setStatus("DISPATCHED");
		order.setUrgency("CRITICAL");
		order.setCategory("ELECTRICAL");
		order.setLocation("第一教学楼三层");
		order.setTechnicianId(previous.getId());
		order.setTechnicianUsername(previous.getUsername());
		order.setTechnicianName(previous.getTechnicianName());
		order.setDispatchDeadline(now.minusMinutes(1));

		when(orders.lockExpiredDispatches(any(), any())).thenReturn(List.of(order));
		when(orders.lockPendingAutoDispatches(any(), any())).thenReturn(List.of());
		when(technicians.findById(previous.getId())).thenReturn(Optional.of(previous));
		when(technicians.lockAutoDispatchCandidates()).thenReturn(List.of(previous, replacement));

		int processed = service.processDispatchGovernance(now, 100);

		assertThat(processed).isEqualTo(1);
		assertThat(order.getTechnicianId()).isEqualTo(replacement.getId());
		assertThat(order.getStatus()).isEqualTo("DISPATCHED");
		assertThat(order.getDispatchDeadline()).isEqualTo(now.plusMinutes(10));
		assertThat(previous.getCurrentLoad()).isZero();
		assertThat(replacement.getCurrentLoad()).isEqualTo(1);
	}

	@Test
	void reconcilesStoredTechnicianLoadWithActiveOrders() {
		MaintenanceTechnician technician = technician("technician-1", "repair-user", 9);
		when(technicians.findAll()).thenReturn(List.of(technician));
		when(orders.countByTechnicianIdAndStatusIn(any(), any())).thenReturn(2L);

		assertThat(service.reconcileTechnicianLoads()).isEqualTo(1);
		assertThat(technician.getCurrentLoad()).isEqualTo(2);
		verify(technicians).save(technician);
	}

	@Test
	void rejectsRepairWithoutEvidencePhotoBeforeWritingData() {
		assertThatThrownBy(() -> service.create(command(List.of()), "reporter"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("故障照片");

		verify(orders, never()).save(any());
		verify(fileService, never()).bind(any(), any(), any(), any());
	}

	@Test
	void grantsTechnicianRoleWhenEmployeeBecomesMaintenanceTechnician() {
		Employee employee = new Employee();
		employee.setId("employee-1");
		employee.setEmployeeCode("202600127");
		employee.setEmployeeName("维修人员一号");
		employee.setPhone("13800000001");
		AdminUser account = new AdminUser();
		account.setId("user-1");
		account.setUsername("repair-user");
		account.setTokenVersion(3);
		Role technicianRole = new Role();
		technicianRole.setRoleCode("FACILITY_TECHNICIAN");

		when(employees.findById("employee-1")).thenReturn(Optional.of(employee));
		when(users.findByEmployeeId("employee-1")).thenReturn(Optional.of(account));
		when(roles.findByRoleCode("FACILITY_TECHNICIAN")).thenReturn(technicianRole);
		when(technicians.findByEmployeeId("employee-1")).thenReturn(Optional.empty());
		when(technicians.save(any(MaintenanceTechnician.class))).thenAnswer(invocation -> invocation.getArgument(0));

		var result = service.saveTechnician(null,
				new TechnicianCommand("employee-1", "ELECTRICAL", true, true, "AVAILABLE", 5,
						"ALL", "ALL", "ALL", null, null));

		assertThat(result.username()).isEqualTo("repair-user");
		assertThat(account.getRoles()).containsExactly(technicianRole);
		assertThat(account.getTokenVersion()).isEqualTo(4);
		verify(users).save(account);
	}

	private CreateRepairOrder command(List<String> photoIds) {
		return new CreateRepairOrder(
				"教学楼照明故障",
				"ELECTRICAL",
				"三楼走廊照明无法开启",
				"第一教学楼三层",
				"NORMAL",
				"报修用户",
				"13800000000",
				"AUTO",
				photoIds);
	}

	private MaintenanceTechnician technician(String id, String username, int currentLoad) {
		MaintenanceTechnician value = new MaintenanceTechnician();
		value.setId(id);
		value.setUsername(username);
		value.setTechnicianName(username);
		value.setSkillCategories("ELECTRICAL");
		value.setCurrentLoad(currentLoad);
		value.setMaxConcurrent(5);
		return value;
	}
}
