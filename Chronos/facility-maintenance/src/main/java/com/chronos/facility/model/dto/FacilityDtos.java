package com.chronos.facility.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.chronos.facility.model.MaintenanceLedgerEntry;
import com.chronos.facility.model.MaintenanceMaterial;
import com.chronos.facility.model.MaintenanceTechnician;
import com.chronos.facility.model.MaterialRequest;
import com.chronos.facility.model.MaterialRequestItem;
import com.chronos.facility.model.RepairOrder;
import com.chronos.facility.model.RepairOrderEvent;
import com.chronos.file.model.ManagedFileView;

public final class FacilityDtos {
	private FacilityDtos() {
	}

	public record CreateRepairOrder(
			String title,
			String category,
			String description,
			String location,
			String urgency,
			String contactName,
			String contactPhone,
			String dispatchMode,
			List<String> reportFileIds) {
	}

	public record DispatchCommand(String technicianId, String remark) {
	}

	public record CompleteCommand(String note) {
	}

	public record CloseCommand(Integer rating, String comment) {
	}

	public record ActionCommand(String remark) {
	}

	public record TechnicianCommand(
			String employeeId,
			String skillCategories,
			Boolean active,
			Boolean autoDispatch,
			String workStatus,
			Integer maxConcurrent) {
	}

	public record MaterialCommand(
			String materialCode,
			String materialName,
			String specification,
			String unit,
			BigDecimal safetyStock,
			BigDecimal unitCost,
			String status) {
	}

	public record StockCommand(BigDecimal quantity, String remark) {
	}

	public record MaterialRequestLine(String materialId, BigDecimal quantity) {
	}

	public record CreateMaterialRequest(String repairOrderId, String remark, List<MaterialRequestLine> items) {
	}

	public record ApprovalLine(String itemId, BigDecimal approvedQuantity) {
	}

	public record ReviewMaterialRequest(boolean approved, String comment, List<ApprovalLine> items) {
	}

	public record RepairOrderView(
			String id,
			String orderNo,
			String title,
			String category,
			String description,
			String location,
			String urgency,
			String contactName,
			String contactPhone,
			String reporterUsername,
			String reporterName,
			String status,
			String dispatchMode,
			String technicianId,
			String technicianUsername,
			String technicianName,
			LocalDateTime createTime,
			LocalDateTime dispatchedAt,
			LocalDateTime acceptedAt,
			LocalDateTime startedAt,
			LocalDateTime completedAt,
			LocalDateTime closedAt,
			String completionNote,
			String closeComment,
			Integer rating,
			List<ManagedFileView> reportFiles,
			List<ManagedFileView> completionFiles,
			List<RepairOrderEventView> events,
			List<MaterialRequestView> materialRequests) {
	}

	public record RepairOrderEventView(
			String eventType,
			String fromStatus,
			String toStatus,
			String operatorUsername,
			String remark,
			LocalDateTime createTime) {
		public static RepairOrderEventView from(RepairOrderEvent value) {
			return new RepairOrderEventView(value.getEventType(), value.getFromStatus(), value.getToStatus(),
					value.getOperatorUsername(), value.getRemark(), value.getCreateTime());
		}
	}

	public record TechnicianView(
			String id,
			String employeeId,
			String username,
			String employeeCode,
			String technicianName,
			String phone,
			String skillCategories,
			String workStatus,
			Boolean active,
			Boolean autoDispatch,
			Integer currentLoad,
			Integer maxConcurrent) {
		public static TechnicianView from(MaintenanceTechnician value) {
			return new TechnicianView(value.getId(), value.getEmployeeId(), value.getUsername(),
					value.getEmployeeCode(), value.getTechnicianName(), value.getPhone(), value.getSkillCategories(),
					value.getWorkStatus(), value.getActive(), value.getAutoDispatch(), value.getCurrentLoad(),
					value.getMaxConcurrent());
		}
	}

	public record MaterialView(
			String id,
			String materialCode,
			String materialName,
			String specification,
			String unit,
			BigDecimal stockQuantity,
			BigDecimal reservedQuantity,
			BigDecimal availableQuantity,
			BigDecimal safetyStock,
			BigDecimal unitCost,
			String status,
			boolean lowStock) {
		public static MaterialView from(MaintenanceMaterial value) {
			BigDecimal available = value.getStockQuantity().subtract(value.getReservedQuantity());
			return new MaterialView(value.getId(), value.getMaterialCode(), value.getMaterialName(),
					value.getSpecification(), value.getUnit(), value.getStockQuantity(), value.getReservedQuantity(),
					available, value.getSafetyStock(), value.getUnitCost(), value.getStatus(),
					available.compareTo(value.getSafetyStock()) <= 0);
		}
	}

	public record MaterialRequestItemView(
			String id,
			String materialId,
			String materialCode,
			String materialName,
			BigDecimal requestedQuantity,
			BigDecimal approvedQuantity,
			BigDecimal issuedQuantity) {
		public static MaterialRequestItemView from(MaterialRequestItem value) {
			return new MaterialRequestItemView(value.getId(), value.getMaterialId(), value.getMaterialCode(),
					value.getMaterialName(), value.getRequestedQuantity(), value.getApprovedQuantity(),
					value.getIssuedQuantity());
		}
	}

	public record MaterialRequestView(
			String id,
			String requestNo,
			String repairOrderId,
			String applicantUsername,
			String applicantName,
			String status,
			String remark,
			String reviewedBy,
			LocalDateTime reviewedAt,
			String reviewComment,
			String issuedBy,
			LocalDateTime issuedAt,
			LocalDateTime createTime,
			List<MaterialRequestItemView> items) {
	}

	public record LedgerView(
			String id,
			String repairOrderId,
			String materialId,
			String entryType,
			BigDecimal quantity,
			BigDecimal amount,
			String operatorUsername,
			String description,
			LocalDateTime createTime) {
		public static LedgerView from(MaintenanceLedgerEntry value) {
			return new LedgerView(value.getId(), value.getRepairOrderId(), value.getMaterialId(),
					value.getEntryType(), value.getQuantity(), value.getAmount(), value.getOperatorUsername(),
					value.getDescription(), value.getCreateTime());
		}
	}
}
