package com.chronos.facility.service;

import com.chronos.facility.dao.FacilityAssetRepository;
import com.chronos.facility.dao.FacilitySafetyIncidentRepository;
import com.chronos.facility.dao.InventoryTransactionRepository;
import com.chronos.facility.dao.MaintenanceMaterialRepository;
import com.chronos.facility.dao.PreventiveMaintenancePlanRepository;
import com.chronos.facility.model.FacilityAsset;
import com.chronos.facility.model.FacilitySafetyIncident;
import com.chronos.facility.model.InventoryTransaction;
import com.chronos.facility.model.MaintenanceMaterial;
import com.chronos.facility.model.PreventiveMaintenancePlan;
import com.chronos.service.iService.IAuditLogService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 资产、预防性保养、库存流水与后勤安全事件的生产状态机。 */
@Service
public class FacilityAssetGovernanceService {
	private final FacilityAssetRepository assets;
	private final PreventiveMaintenancePlanRepository plans;
	private final InventoryTransactionRepository transactions;
	private final FacilitySafetyIncidentRepository incidents;
	private final MaintenanceMaterialRepository materials;
	private final IAuditLogService audit;

	public FacilityAssetGovernanceService(
			FacilityAssetRepository assets,
			PreventiveMaintenancePlanRepository plans,
			InventoryTransactionRepository transactions,
			FacilitySafetyIncidentRepository incidents,
			MaintenanceMaterialRepository materials,
			IAuditLogService audit) {
		this.assets = assets;
		this.plans = plans;
		this.transactions = transactions;
		this.incidents = incidents;
		this.materials = materials;
		this.audit = audit;
	}

	@Transactional(readOnly = true)
	public List<FacilityAsset> assets() {
		return assets.findAllByOrderByAssetCode();
	}

	@Transactional(readOnly = true)
	public FacilityAsset assetByQr(String token) {
		return assets.findByQrToken(token)
				.orElseThrow(() -> new IllegalArgumentException("资产二维码无效"));
	}

	@Transactional
	public FacilityAsset saveAsset(String id, AssetCommand command) {
		require(command != null, "资产信息不能为空");
		FacilityAsset value = id == null ? new FacilityAsset() : assets.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("资产不存在"));
		if (id == null && assets.findByAssetCode(text(command.assetCode(), "资产编码不能为空")).isPresent()) {
			throw new IllegalStateException("资产编码已存在");
		}
		value.setAssetCode(text(command.assetCode(), "资产编码不能为空"));
		value.setAssetName(text(command.assetName(), "资产名称不能为空"));
		value.setCategory(text(command.category(), "资产分类不能为空"));
		value.setSerialNumber(trim(command.serialNumber()));
		value.setOrganizationId(trim(command.organizationId()));
		value.setLocation(text(command.location(), "资产位置不能为空"));
		value.setPurchaseDate(command.purchaseDate());
		value.setWarrantyExpiry(command.warrantyExpiry());
		value.setStatus(enumValue(command.status(), Set.of("IN_SERVICE", "OUT_OF_SERVICE", "UNDER_REPAIR", "RETIRED"), "IN_SERVICE", "资产状态无效"));
		if (value.getQrToken() == null) {
			value.setQrToken(UUID.randomUUID().toString().replace("-", ""));
		}
		return assets.save(value);
	}

	@Transactional(readOnly = true)
	public List<PreventiveMaintenancePlan> plans() {
		return plans.findAllByOrderByNextDueDate();
	}

	@Transactional
	public PreventiveMaintenancePlan createPlan(PlanCommand command) {
		FacilityAsset asset = assets.findById(command.assetId())
				.orElseThrow(() -> new IllegalArgumentException("资产不存在"));
		if (command.intervalDays() == null || command.intervalDays() < 1) {
			throw new IllegalArgumentException("保养周期必须大于零");
		}
		PreventiveMaintenancePlan value = new PreventiveMaintenancePlan();
		value.setAssetId(asset.getId());
		value.setPlanName(text(command.planName(), "计划名称不能为空"));
		value.setIntervalDays(command.intervalDays());
		value.setNextDueDate(command.nextDueDate() == null ? LocalDate.now() : command.nextDueDate());
		value.setAssignedUsername(trim(command.assignedUsername()));
		value.setChecklist(text(command.checklist(), "保养检查清单不能为空"));
		asset.setNextMaintenanceDate(value.getNextDueDate());
		assets.save(asset);
		return plans.save(value);
	}

	@Transactional
	public PreventiveMaintenancePlan completePlan(String id, String note, String actor) {
		PreventiveMaintenancePlan value = plans.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("保养计划不存在"));
		if (!Set.of("ACTIVE", "OVERDUE").contains(value.getStatus())) {
			throw new IllegalStateException("当前保养计划不可执行");
		}
		value.setLastCompletedAt(LocalDateTime.now());
		value.setLastCompletedBy(actor);
		value.setCompletionNote(text(note, "保养完成说明不能为空"));
		value.setNextDueDate(LocalDate.now().plusDays(value.getIntervalDays()));
		value.setStatus("ACTIVE");
		FacilityAsset asset = assets.findById(value.getAssetId()).orElseThrow();
		asset.setNextMaintenanceDate(value.getNextDueDate());
		assets.save(asset);
		audit.log(actor, "FACILITY_PREVENTIVE_COMPLETE", "planId=" + id);
		return plans.save(value);
	}

	@Transactional(readOnly = true)
	public List<InventoryTransaction> transactions() {
		return transactions.findAllByOrderByCreateTimeDesc();
	}

	@Transactional
	public InventoryTransaction adjustInventory(InventoryCommand command, String actor) {
		require(command != null, "库存流水不能为空");
		String key = text(command.idempotencyKey(), "幂等键不能为空");
		return transactions.findByIdempotencyKey(key).orElseGet(() -> {
			MaintenanceMaterial material = materials.findById(command.materialId())
					.orElseThrow(() -> new IllegalArgumentException("维修物料不存在"));
			String type = enumValue(command.transactionType(), Set.of("IN", "OUT", "RETURN", "ADJUST"), null, "库存流水类型无效");
			BigDecimal quantity = command.quantity();
			if (quantity == null || quantity.signum() == 0) {
				throw new IllegalArgumentException("库存变动数量不能为零");
			}
			if (Set.of("IN", "RETURN").contains(type) && quantity.signum() < 0) quantity = quantity.negate();
			if ("OUT".equals(type) && quantity.signum() > 0) quantity = quantity.negate();
			BigDecimal before = material.getStockQuantity();
			BigDecimal after = before.add(quantity);
			if (after.signum() < 0 || after.compareTo(material.getReservedQuantity()) < 0) {
				throw new IllegalStateException("库存调整后数量不足以覆盖已预留库存");
			}
			material.setStockQuantity(after);
			materials.save(material);
			InventoryTransaction value = new InventoryTransaction();
			value.setMaterialId(material.getId());
			value.setTransactionType(type);
			value.setQuantity(quantity);
			value.setBeforeQuantity(before);
			value.setAfterQuantity(after);
			value.setReferenceType(trim(command.referenceType()));
			value.setReferenceId(trim(command.referenceId()));
			value.setOperatorUsername(actor);
			value.setReason(text(command.reason(), "库存变动原因不能为空"));
			value.setIdempotencyKey(key);
			return transactions.save(value);
		});
	}

	@Transactional(readOnly = true)
	public List<FacilitySafetyIncident> incidents() {
		return incidents.findAllByOrderByOccurredAtDesc();
	}

	@Transactional
	public FacilitySafetyIncident reportIncident(IncidentCommand command, String actor) {
		String severity = enumValue(command.severity(), Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL"), null, "安全事件等级无效");
		FacilitySafetyIncident value = new FacilitySafetyIncident();
		value.setRepairOrderId(trim(command.repairOrderId()));
		value.setAssetId(trim(command.assetId()));
		value.setOccurredAt(command.occurredAt() == null ? LocalDateTime.now() : command.occurredAt());
		value.setSeverity(severity);
		value.setDescription(text(command.description(), "安全事件描述不能为空"));
		value.setEmergencyAction(text(command.emergencyAction(), "应急处置不能为空"));
		value.setStatus(Set.of("HIGH", "CRITICAL").contains(severity) ? "INVESTIGATING" : "REPORTED");
		value.setReportedBy(actor);
		audit.log(actor, "FACILITY_SAFETY_REPORT", "severity=" + severity);
		return incidents.save(value);
	}

	@Transactional
	public FacilitySafetyIncident closeIncident(String id, String comment, String actor) {
		FacilitySafetyIncident value = incidents.findForUpdate(id)
				.orElseThrow(() -> new IllegalArgumentException("后勤安全事件不存在"));
		if ("CLOSED".equals(value.getStatus())) return value;
		if (actor.equals(value.getReportedBy())) {
			throw new AccessDeniedException("安全事件上报人不能独立关闭自己上报的事件");
		}
		value.setClosureComment(text(comment, "结案说明不能为空"));
		value.setClosedBy(actor);
		value.setClosedAt(LocalDateTime.now());
		value.setStatus("CLOSED");
		return incidents.save(value);
	}

	private String text(String value, String message) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
		return value.trim();
	}

	private String trim(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private String enumValue(String value, Set<String> allowed, String defaultValue, String message) {
		String normalized = value == null || value.isBlank() ? defaultValue : value.trim().toUpperCase(Locale.ROOT);
		if (normalized == null || !allowed.contains(normalized)) throw new IllegalArgumentException(message);
		return normalized;
	}

	private void require(boolean condition, String message) {
		if (!condition) throw new IllegalArgumentException(message);
	}

	public record AssetCommand(String assetCode, String assetName, String category, String serialNumber,
			String organizationId, String location, LocalDate purchaseDate, LocalDate warrantyExpiry, String status) {
	}
	public record PlanCommand(String assetId, String planName, Integer intervalDays, LocalDate nextDueDate,
			String assignedUsername, String checklist) {
	}
	public record InventoryCommand(String materialId, String transactionType, BigDecimal quantity,
			String referenceType, String referenceId, String reason, String idempotencyKey) {
	}
	public record IncidentCommand(String repairOrderId, String assetId, LocalDateTime occurredAt,
			String severity, String description, String emergencyAction) {
	}
}
