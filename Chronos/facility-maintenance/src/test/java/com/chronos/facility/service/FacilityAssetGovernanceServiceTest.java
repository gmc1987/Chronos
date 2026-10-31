package com.chronos.facility.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chronos.facility.dao.FacilityAssetRepository;
import com.chronos.facility.dao.FacilitySafetyIncidentRepository;
import com.chronos.facility.dao.InventoryTransactionRepository;
import com.chronos.facility.dao.MaintenanceMaterialRepository;
import com.chronos.facility.dao.PreventiveMaintenancePlanRepository;
import com.chronos.facility.model.FacilitySafetyIncident;
import com.chronos.facility.model.InventoryTransaction;
import com.chronos.facility.model.MaintenanceMaterial;
import com.chronos.facility.service.FacilityAssetGovernanceService.InventoryCommand;
import com.chronos.service.iService.IAuditLogService;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class FacilityAssetGovernanceServiceTest {
	@Mock FacilityAssetRepository assets;
	@Mock PreventiveMaintenancePlanRepository plans;
	@Mock InventoryTransactionRepository transactions;
	@Mock FacilitySafetyIncidentRepository incidents;
	@Mock MaintenanceMaterialRepository materials;
	@Mock IAuditLogService audit;
	FacilityAssetGovernanceService service;

	@BeforeEach
	void setUp() {
		service = new FacilityAssetGovernanceService(
				assets, plans, transactions, incidents, materials, audit);
	}

	@Test
	void inventoryAdjustmentPersistsBeforeAndAfterQuantities() {
		MaintenanceMaterial material = new MaintenanceMaterial();
		material.setId("material-1");
		material.setStockQuantity(new BigDecimal("10.000"));
		material.setReservedQuantity(new BigDecimal("2.000"));
		when(transactions.findByIdempotencyKey("adjust-1")).thenReturn(Optional.empty());
		when(materials.findById("material-1")).thenReturn(Optional.of(material));
		when(transactions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		InventoryTransaction result = service.adjustInventory(new InventoryCommand(
				"material-1", "OUT", new BigDecimal("3"), "COUNT", "count-1", "盘点出库", "adjust-1"), "keeper");

		assertThat(result.getQuantity()).isEqualByComparingTo("-3");
		assertThat(result.getBeforeQuantity()).isEqualByComparingTo("10");
		assertThat(result.getAfterQuantity()).isEqualByComparingTo("7");
		verify(materials).save(material);
	}

	@Test
	void repeatedIdempotencyKeyDoesNotMutateInventoryAgain() {
		InventoryTransaction existing = new InventoryTransaction();
		existing.setIdempotencyKey("adjust-1");
		when(transactions.findByIdempotencyKey("adjust-1")).thenReturn(Optional.of(existing));

		InventoryTransaction result = service.adjustInventory(new InventoryCommand(
				"material-1", "IN", BigDecimal.ONE, null, null, "入库", "adjust-1"), "keeper");

		assertThat(result).isSameAs(existing);
		verify(materials, never()).findById(any());
	}

	@Test
	void safetyIncidentReporterCannotCloseOwnIncident() {
		FacilitySafetyIncident incident = new FacilitySafetyIncident();
		incident.setStatus("INVESTIGATING");
		incident.setReportedBy("reporter");
		when(incidents.findForUpdate("incident-1")).thenReturn(Optional.of(incident));

		assertThatThrownBy(() -> service.closeIncident("incident-1", "已整改", "reporter"))
				.isInstanceOf(AccessDeniedException.class)
				.hasMessage("安全事件上报人不能独立关闭自己上报的事件");
		verify(incidents, never()).save(any());
	}
}
