package com.chronos.facility.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import com.chronos.facility.dao.InventoryTransactionRepository;
import com.chronos.facility.model.MaintenanceLedgerEntry;
import com.chronos.facility.model.MaintenanceMaterial;
import com.chronos.facility.model.MaintenanceTechnician;
import com.chronos.facility.model.MaterialRequest;
import com.chronos.facility.model.MaterialRequestItem;
import com.chronos.facility.model.RepairOrder;
import com.chronos.facility.model.RepairOrderEvent;
import com.chronos.facility.model.InventoryTransaction;
import com.chronos.facility.model.dto.FacilityDtos.ActionCommand;
import com.chronos.facility.model.dto.FacilityDtos.ApprovalLine;
import com.chronos.facility.model.dto.FacilityDtos.CloseCommand;
import com.chronos.facility.model.dto.FacilityDtos.CompleteCommand;
import com.chronos.facility.model.dto.FacilityDtos.CreateMaterialRequest;
import com.chronos.facility.model.dto.FacilityDtos.CreateRepairOrder;
import com.chronos.facility.model.dto.FacilityDtos.DispatchCommand;
import com.chronos.facility.model.dto.FacilityDtos.LedgerView;
import com.chronos.facility.model.dto.FacilityDtos.MaterialCommand;
import com.chronos.facility.model.dto.FacilityDtos.MaterialRequestItemView;
import com.chronos.facility.model.dto.FacilityDtos.MaterialRequestView;
import com.chronos.facility.model.dto.FacilityDtos.MaterialView;
import com.chronos.facility.model.dto.FacilityDtos.OrganizationOption;
import com.chronos.facility.model.dto.FacilityDtos.RepairOrderView;
import com.chronos.facility.model.dto.FacilityDtos.ReviewMaterialRequest;
import com.chronos.facility.model.dto.FacilityDtos.StockCommand;
import com.chronos.facility.model.dto.FacilityDtos.TechnicianCommand;
import com.chronos.facility.model.dto.FacilityDtos.TechnicianView;
import com.chronos.file.dao.ManagedFileRepository;
import com.chronos.file.model.ManagedFileView;
import com.chronos.file.service.ManagedFileService;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.Role;
import com.chronos.service.iService.IAuditLogService;

@Service
public class FacilityMaintenanceService {
	public static final String REPORT_FILE_TYPE = "FACILITY_REPAIR_REPORT";
	public static final String COMPLETION_FILE_TYPE = "FACILITY_REPAIR_COMPLETION";
	private static final Set<String> ACTIVE_ORDER_STATUSES = Set.of(
			"DISPATCHED", "ACCEPTED", "IN_PROGRESS", "WAITING_MATERIAL", "COMPLETED");

	private final RepairOrderRepository orders;
	private final MaintenanceTechnicianRepository technicians;
	private final MaintenanceMaterialRepository materials;
	private final MaterialRequestRepository requests;
	private final MaterialRequestItemRepository requestItems;
	private final MaintenanceLedgerRepository ledger;
	private final RepairOrderEventRepository events;
	private final InventoryTransactionRepository inventoryTransactions;
	private final IEmployeeRepository employees;
	private final IOrganizationRepository organizations;
	private final IAdminUserRepository users;
	private final IRoleRepository roles;
	private final ManagedFileRepository files;
	private final ManagedFileService fileService;
	private final IAuditLogService audit;
	@Value("${chronos.facility.dispatch.acceptance-minutes.normal:60}")
	private int normalAcceptanceMinutes = 60;
	@Value("${chronos.facility.dispatch.acceptance-minutes.urgent:30}")
	private int urgentAcceptanceMinutes = 30;
	@Value("${chronos.facility.dispatch.acceptance-minutes.critical:10}")
	private int criticalAcceptanceMinutes = 10;
	@Value("${chronos.facility.dispatch.retry-minutes.normal:15}")
	private int normalRetryMinutes = 15;
	@Value("${chronos.facility.dispatch.retry-minutes.urgent:5}")
	private int urgentRetryMinutes = 5;
	@Value("${chronos.facility.dispatch.retry-minutes.critical:1}")
	private int criticalRetryMinutes = 1;

	public FacilityMaintenanceService(
			RepairOrderRepository orders,
			MaintenanceTechnicianRepository technicians,
			MaintenanceMaterialRepository materials,
			MaterialRequestRepository requests,
			MaterialRequestItemRepository requestItems,
			MaintenanceLedgerRepository ledger,
			RepairOrderEventRepository events,
			InventoryTransactionRepository inventoryTransactions,
			IEmployeeRepository employees,
			IOrganizationRepository organizations,
			IAdminUserRepository users,
			IRoleRepository roles,
			ManagedFileRepository files,
			ManagedFileService fileService,
			IAuditLogService audit) {
		this.orders = orders;
		this.technicians = technicians;
		this.materials = materials;
		this.requests = requests;
		this.requestItems = requestItems;
		this.ledger = ledger;
		this.events = events;
		this.inventoryTransactions = inventoryTransactions;
		this.employees = employees;
		this.organizations = organizations;
		this.users = users;
		this.roles = roles;
		this.files = files;
		this.fileService = fileService;
		this.audit = audit;
	}

	@Transactional
	public RepairOrderView create(CreateRepairOrder command, String actor) {
		require(command != null, "报修信息不能为空");
		requireText(command.title(), "请填写故障标题");
		requireText(command.category(), "请选择故障分类");
		requireText(command.description(), "请填写故障描述");
		requireText(command.location(), "请填写故障位置");
		require(command.reportFileIds() != null && !command.reportFileIds().isEmpty(), "请至少上传一张故障照片");
		AdminUser account = account(actor);
		RepairOrder order = new RepairOrder();
		order.setOrderNo(number("BX", orders.nextOrderSequence()));
		order.setTitle(limit(command.title(), 200));
		order.setCategory(normalizeCategory(command.category()));
		order.setDescription(command.description().trim());
		order.setLocation(limit(command.location(), 300));
		order.setUrgency(normalize(command.urgency(), Set.of("NORMAL", "URGENT", "CRITICAL"), "NORMAL", "紧急程度无效"));
		order.setContactName(limit(or(command.contactName(), account.getDisplayName(), actor), 100));
		order.setContactPhone(limit(or(command.contactPhone(), account.getPhone()), 32));
		order.setReporterUsername(actor);
		order.setReporterName(limit(or(account.getDisplayName(), actor), 100));
		order.setOrganizationId(account.getOrganizationId());
		order.setDispatchMode(normalize(command.dispatchMode(), Set.of("AUTO", "MANUAL"), "AUTO", "派单方式无效"));
		order = orders.save(order);
		fileService.bind(command.reportFileIds(), REPORT_FILE_TYPE, order.getId(), actor);
		recordEvent(order, "SUBMIT", null, order.getStatus(), actor, "提交故障报修");
		if ("AUTO".equals(order.getDispatchMode())) {
			autoDispatch(order, actor);
		}
		audit.log(actor, "FACILITY_REPAIR_CREATE", "orderId=" + order.getId());
		return view(order, true);
	}

	@Transactional(readOnly = true)
	public Page<RepairOrderView> adminOrders(String keyword, String status, int page, int size) {
		Specification<RepairOrder> specification = (root, query, builder) -> builder.conjunction();
		if (status != null && !status.isBlank()) {
			specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
		}
		if (keyword != null && !keyword.isBlank()) {
			String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
			specification = specification.and((root, query, builder) -> builder.or(
					builder.like(builder.lower(root.get("orderNo")), value),
					builder.like(builder.lower(root.get("title")), value),
					builder.like(builder.lower(root.get("location")), value),
					builder.like(builder.lower(root.get("reporterName")), value),
					builder.like(builder.lower(root.get("technicianName")), value)));
		}
		return orders.findAll(specification, page(page, size)).map(value -> view(value, false));
	}

	@Transactional(readOnly = true)
	public Page<RepairOrderView> myOrders(String actor, int page, int size) {
		return orders.findByReporterUsernameOrderByCreateTimeDesc(actor, page(page, size))
				.map(value -> view(value, false));
	}

	@Transactional(readOnly = true)
	public Page<RepairOrderView> assignedOrders(String actor, int page, int size) {
		return orders.findByTechnicianUsernameOrderByCreateTimeDesc(actor, page(page, size))
				.map(value -> view(value, false));
	}

	@Transactional(readOnly = true)
	public RepairOrderView detail(String id, String actor, boolean administrator) {
		RepairOrder order = order(id);
		if (!administrator && !actor.equals(order.getReporterUsername()) && !actor.equals(order.getTechnicianUsername())) {
			throw new AccessDeniedException("无权查看该报修单");
		}
		return view(order, true);
	}

	@Transactional
	public RepairOrderView dispatch(String id, DispatchCommand command, String actor) {
		require(command != null && command.technicianId() != null, "请选择维修人员");
		RepairOrder order = order(id);
		require(Set.of("PENDING_DISPATCH", "DISPATCHED").contains(order.getStatus()), "当前状态不能派单");
		MaintenanceTechnician target = technician(command.technicianId());
		require(Boolean.TRUE.equals(target.getActive()) && !"OFFLINE".equals(target.getWorkStatus()), "维修人员当前不可派单");
		require(target.getCurrentLoad() < target.getMaxConcurrent(), "维修人员当前工单已满");
		if (order.getTechnicianId() != null && !order.getTechnicianId().equals(target.getId())) {
			releaseTechnician(order.getTechnicianId());
		}
		assign(order, target, "MANUAL", actor, command.remark());
		return view(order, true);
	}

	@Transactional
	public RepairOrderView retryAutoDispatch(String id, String actor) {
		RepairOrder order = order(id);
		require("PENDING_DISPATCH".equals(order.getStatus()), "只有待派单工单可以重新自动派单");
		order.setDispatchMode("AUTO");
		autoDispatch(order, actor);
		return view(order, true);
	}

	@Transactional
	public RepairOrderView accept(String id, String actor) {
		RepairOrder order = assigned(id, actor);
		transition(order, Set.of("DISPATCHED"), "ACCEPTED", "ACCEPT", actor, "维修人员接单");
		order.setAcceptedAt(LocalDateTime.now());
		order.setDispatchDeadline(null);
		return view(orders.save(order), true);
	}

	@Transactional
	public RepairOrderView start(String id, String actor) {
		RepairOrder order = assigned(id, actor);
		transition(order, Set.of("ACCEPTED", "WAITING_MATERIAL"), "IN_PROGRESS", "START", actor, "开始维修");
		if (order.getStartedAt() == null) order.setStartedAt(LocalDateTime.now());
		return view(orders.save(order), true);
	}

	@Transactional
	public RepairOrderView complete(String id, CompleteCommand command, String actor) {
		RepairOrder order = assigned(id, actor);
		require(Set.of("IN_PROGRESS", "WAITING_MATERIAL").contains(order.getStatus()), "当前状态不能完工");
		requireText(command == null ? null : command.note(), "请填写维修结果");
		require(!requests.existsByRepairOrderIdAndStatusIn(id, Set.of("SUBMITTED", "APPROVED")), "仍有未完成的物料申请");
		require(!fileViews(COMPLETION_FILE_TYPE, id).isEmpty(), "请至少上传一张维修完成照片");
		transition(order, Set.of(order.getStatus()), "COMPLETED", "COMPLETE", actor, command.note());
		order.setCompletionNote(command.note().trim());
		order.setCompletedAt(LocalDateTime.now());
		return view(orders.save(order), true);
	}

	@Transactional
	public RepairOrderView close(String id, CloseCommand command, String actor) {
		RepairOrder order = order(id);
		require(actor.equals(order.getReporterUsername()), "只能由报修人确认关闭");
		require("COMPLETED".equals(order.getStatus()), "只有已完工工单可以确认关闭");
		int rating = command == null || command.rating() == null ? 5 : command.rating();
		require(rating >= 1 && rating <= 5, "评分必须为1到5分");
		transition(order, Set.of("COMPLETED"), "CLOSED", "CLOSE", actor,
				command == null ? null : command.comment());
		order.setRating(rating);
		order.setCloseComment(command == null ? null : command.comment());
		order.setClosedAt(LocalDateTime.now());
		releaseTechnician(order.getTechnicianId());
		ledger(order.getId(), null, "ORDER_CLOSED", null, BigDecimal.ZERO, actor,
				"维修工单完成闭环，评分" + rating + "分");
		return view(orders.save(order), true);
	}

	@Transactional
	public RepairOrderView cancel(String id, ActionCommand command, String actor, boolean administrator) {
		RepairOrder order = order(id);
		require(administrator || actor.equals(order.getReporterUsername()), "无权取消该报修单");
		require(Set.of("PENDING_DISPATCH", "DISPATCHED").contains(order.getStatus()), "当前状态不能取消");
		requireText(command == null ? null : command.remark(), "请填写取消原因");
		String old = order.getStatus();
		order.setStatus("CANCELLED");
		order.setCancelReason(command.remark().trim());
		recordEvent(order, "CANCEL", old, "CANCELLED", actor, command.remark());
		releaseTechnician(order.getTechnicianId());
		return view(orders.save(order), true);
	}

	@Transactional(readOnly = true)
	public List<TechnicianView> technicians() {
		return technicians.findAll(Sort.by("technicianName")).stream().map(TechnicianView::from).toList();
	}

	@Transactional(readOnly = true)
	public List<OrganizationOption> organizationOptions() {
		return organizations.findAll(Sort.by("sortOrder").ascending().and(Sort.by("organizationName"))).stream()
				.filter(value -> Integer.valueOf(1).equals(value.getStatus()))
				.map(value -> new OrganizationOption(value.getId(), value.getOrganizationName(), value.getOrganizationType()))
				.toList();
	}

	@Transactional
	public TechnicianView saveTechnician(String id, TechnicianCommand command) {
		require(command != null && command.employeeId() != null, "请选择员工");
		Employee employee = employees.findById(command.employeeId())
				.orElseThrow(() -> new IllegalArgumentException("员工不存在"));
		AdminUser account = users.findByEmployeeId(employee.getId())
				.orElseThrow(() -> new IllegalArgumentException("该员工尚未开通登录账号"));
		grantTechnicianRole(account);
		MaintenanceTechnician value = id == null ? new MaintenanceTechnician() : technician(id);
		if (id == null) {
			require(technicians.findByEmployeeId(employee.getId()).isEmpty(), "该员工已经是维修人员");
		}
		value.setEmployeeId(employee.getId());
		value.setUsername(account.getUsername());
		value.setEmployeeCode(employee.getEmployeeCode());
		value.setTechnicianName(employee.getEmployeeName());
		value.setPhone(employee.getPhone());
		value.setSkillCategories(required(command.skillCategories(), "请填写技能分类"));
		value.setActive(command.active() == null ? true : command.active());
		value.setAutoDispatch(command.autoDispatch() == null ? true : command.autoDispatch());
		value.setWorkStatus(normalize(command.workStatus(), Set.of("AVAILABLE", "BUSY", "OFFLINE"),
				"AVAILABLE", "工作状态无效"));
		value.setMaxConcurrent(command.maxConcurrent() == null ? 5 : command.maxConcurrent());
		require(value.getMaxConcurrent() > 0 && value.getMaxConcurrent() <= 100, "并发工单上限应在1到100之间");
		value.setResponsibilityOrganizationIds(csvOrAll(command.responsibilityOrganizationIds()));
		value.setResponsibilityAreas(csvOrAll(command.responsibilityAreas()));
		value.setDutyDays(csvOrAll(command.dutyDays()));
		Set<String> configuredDutyDays = csv(value.getDutyDays());
		require(configuredDutyDays.contains("ALL") || Set.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY",
				"FRIDAY", "SATURDAY", "SUNDAY").containsAll(configuredDutyDays), "值班星期配置无效");
		value.setDutyStartTime(command.dutyStartTime() == null ? LocalTime.MIN : command.dutyStartTime());
		value.setDutyEndTime(command.dutyEndTime() == null ? LocalTime.of(23, 59, 59) : command.dutyEndTime());
		return TechnicianView.from(technicians.save(value));
	}

	private void grantTechnicianRole(AdminUser account) {
		Role role = roles.findByRoleCode("FACILITY_TECHNICIAN");
		if (role == null) throw new IllegalStateException("维修人员角色尚未初始化");
		boolean alreadyGranted = account.getRoles().stream()
				.anyMatch(value -> "FACILITY_TECHNICIAN".equals(value.getRoleCode()));
		if (alreadyGranted) return;
		// 维修人员档案和账号权限必须在同一事务内生效，避免出现已派单但账号无权接单。
		account.getRoles().add(role);
		account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
		users.save(account);
	}

	@Transactional(readOnly = true)
	public Page<MaterialView> materials(String keyword, int page, int size) {
		Specification<MaintenanceMaterial> specification = (root, query, builder) -> builder.conjunction();
		if (keyword != null && !keyword.isBlank()) {
			String value = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
			specification = specification.and((root, query, builder) -> builder.or(
					builder.like(builder.lower(root.get("materialCode")), value),
					builder.like(builder.lower(root.get("materialName")), value)));
		}
		return materials.findAll(specification, page(page, size)).map(MaterialView::from);
	}

	@Transactional
	public MaterialView saveMaterial(String id, MaterialCommand command) {
		require(command != null, "物料信息不能为空");
		requireText(command.materialCode(), "请填写物料编码");
		requireText(command.materialName(), "请填写物料名称");
		requireText(command.unit(), "请填写计量单位");
		MaintenanceMaterial value = id == null ? new MaintenanceMaterial() : material(id);
		if (id == null) {
			require(materials.findByMaterialCode(command.materialCode().trim()).isEmpty(), "物料编码已存在");
		}
		value.setMaterialCode(command.materialCode().trim());
		value.setMaterialName(command.materialName().trim());
		value.setSpecification(command.specification());
		value.setUnit(command.unit().trim());
		value.setSafetyStock(nonNegative(command.safetyStock(), "安全库存不能为负数"));
		value.setUnitCost(nonNegative(command.unitCost(), "物料单价不能为负数"));
		value.setStatus(normalize(command.status(), Set.of("ACTIVE", "DISABLED"), "ACTIVE", "物料状态无效"));
		return MaterialView.from(materials.save(value));
	}

	@Transactional
	public MaterialView restock(String id, StockCommand command, String actor) {
		MaintenanceMaterial material = material(id);
		BigDecimal quantity = positive(command == null ? null : command.quantity(), "入库数量必须大于0");
		BigDecimal before = material.getStockQuantity();
		material.setStockQuantity(material.getStockQuantity().add(quantity));
		material = materials.save(material);
		inventoryTransaction(material, "IN", quantity, before, null, null, actor,
				"物料入库" + suffix(command.remark()), "RESTOCK:" + material.getId() + ":" + System.nanoTime());
		ledger(null, material.getId(), "MATERIAL_IN", quantity, quantity.multiply(material.getUnitCost()), actor,
				"物料入库" + suffix(command.remark()));
		return MaterialView.from(material);
	}

	@Transactional
	public MaterialRequestView requestMaterials(CreateMaterialRequest command, String actor) {
		require(command != null && command.items() != null && !command.items().isEmpty(), "请选择申请物料");
		RepairOrder order = assigned(command.repairOrderId(), actor);
		require(Set.of("ACCEPTED", "IN_PROGRESS", "WAITING_MATERIAL").contains(order.getStatus()), "当前工单不能申请物料");
		MaterialRequest request = new MaterialRequest();
		request.setRequestNo(number("WL", requests.nextRequestSequence()));
		request.setRepairOrderId(order.getId());
		request.setApplicantUsername(actor);
		request.setApplicantName(order.getTechnicianName());
		request.setRemark(command.remark());
		request = requests.save(request);
		for (var line : command.items()) {
			MaintenanceMaterial material = material(line.materialId());
			MaterialRequestItem item = new MaterialRequestItem();
			item.setRequestId(request.getId());
			item.setMaterialId(material.getId());
			item.setMaterialCode(material.getMaterialCode());
			item.setMaterialName(material.getMaterialName());
			item.setRequestedQuantity(positive(line.quantity(), "申请数量必须大于0"));
			requestItems.save(item);
		}
		String old = order.getStatus();
		order.setStatus("WAITING_MATERIAL");
		orders.save(order);
		recordEvent(order, "MATERIAL_REQUEST", old, order.getStatus(), actor, "提交物料申请" + request.getRequestNo());
		return requestView(request);
	}

	@Transactional(readOnly = true)
	public Page<MaterialRequestView> materialRequests(String status, int page, int size) {
		Specification<MaterialRequest> specification = (root, query, builder) -> builder.conjunction();
		if (status != null && !status.isBlank()) {
			specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
		}
		return requests.findAll(specification, page(page, size)).map(this::requestView);
	}

	@Transactional
	public MaterialRequestView reviewMaterialRequest(String id, ReviewMaterialRequest command, String actor) {
		MaterialRequest request = request(id);
		require("SUBMITTED".equals(request.getStatus()), "物料申请已经处理");
		require(command != null, "审批信息不能为空");
		request.setReviewedBy(actor);
		request.setReviewedAt(LocalDateTime.now());
		request.setReviewComment(command.comment());
		if (!command.approved()) {
			request.setStatus("REJECTED");
			return requestView(requests.save(request));
		}
		List<ApprovalLine> approvals = command.items() == null ? List.of() : command.items();
		for (MaterialRequestItem item : requestItems.findByRequestIdOrderByCreateTime(id)) {
			BigDecimal approved = approvals.stream().filter(value -> item.getId().equals(value.itemId()))
					.findFirst().map(ApprovalLine::approvedQuantity).orElse(item.getRequestedQuantity());
			approved = positive(approved, "批准数量必须大于0");
			require(approved.compareTo(item.getRequestedQuantity()) <= 0, "批准数量不能超过申请数量");
			MaintenanceMaterial material = material(item.getMaterialId());
			BigDecimal available = material.getStockQuantity().subtract(material.getReservedQuantity());
			require(available.compareTo(approved) >= 0, material.getMaterialName() + "库存不足");
			material.setReservedQuantity(material.getReservedQuantity().add(approved));
			materials.save(material);
			item.setApprovedQuantity(approved);
			requestItems.save(item);
		}
		request.setStatus("APPROVED");
		return requestView(requests.save(request));
	}

	@Transactional
	public MaterialRequestView issueMaterialRequest(String id, String actor) {
		MaterialRequest request = request(id);
		require("APPROVED".equals(request.getStatus()), "只有已批准申请可以出库");
		for (MaterialRequestItem item : requestItems.findByRequestIdOrderByCreateTime(id)) {
			MaintenanceMaterial material = material(item.getMaterialId());
			BigDecimal quantity = item.getApprovedQuantity();
			require(quantity != null && material.getStockQuantity().compareTo(quantity) >= 0,
					material.getMaterialName() + "库存不足");
			BigDecimal before = material.getStockQuantity();
			material.setStockQuantity(material.getStockQuantity().subtract(quantity));
			material.setReservedQuantity(material.getReservedQuantity().subtract(quantity));
			materials.save(material);
			item.setIssuedQuantity(quantity);
			requestItems.save(item);
			inventoryTransaction(material, "OUT", quantity.negate(), before, "MATERIAL_REQUEST", request.getId(), actor,
					"维修领料，申请单" + request.getRequestNo(), "ISSUE:" + request.getId() + ":" + item.getId());
			ledger(request.getRepairOrderId(), material.getId(), "MATERIAL_OUT", quantity.negate(),
					quantity.multiply(material.getUnitCost()), actor,
					"维修领料，申请单" + request.getRequestNo());
		}
		request.setStatus("ISSUED");
		request.setIssuedBy(actor);
		request.setIssuedAt(LocalDateTime.now());
		request = requests.save(request);
		RepairOrder order = order(request.getRepairOrderId());
		if (!requests.existsByRepairOrderIdAndStatusIn(order.getId(), Set.of("SUBMITTED", "APPROVED"))) {
			String old = order.getStatus();
			order.setStatus("IN_PROGRESS");
			orders.save(order);
			recordEvent(order, "MATERIAL_ISSUED", old, "IN_PROGRESS", actor, "物料已出库");
		}
		return requestView(request);
	}

	private void inventoryTransaction(
			MaintenanceMaterial material,
			String type,
			BigDecimal quantity,
			BigDecimal before,
			String referenceType,
			String referenceId,
			String actor,
			String reason,
			String idempotencyKey) {
		if (inventoryTransactions.findByIdempotencyKey(idempotencyKey).isPresent()) {
			return;
		}
		InventoryTransaction value = new InventoryTransaction();
		value.setMaterialId(material.getId());
		value.setTransactionType(type);
		value.setQuantity(quantity);
		value.setBeforeQuantity(before);
		value.setAfterQuantity(material.getStockQuantity());
		value.setReferenceType(referenceType);
		value.setReferenceId(referenceId);
		value.setOperatorUsername(actor);
		value.setReason(reason);
		value.setIdempotencyKey(idempotencyKey);
		inventoryTransactions.save(value);
	}

	@Transactional(readOnly = true)
	public Page<LedgerView> ledger(int page, int size) {
		return ledger.findAll(page(page, size)).map(LedgerView::from);
	}

	private void autoDispatch(RepairOrder order, String actor) {
		autoDispatch(order, actor, null, LocalDateTime.now());
	}

	private void autoDispatch(RepairOrder order, String actor, String excludedTechnicianId, LocalDateTime now) {
		MaintenanceTechnician target = technicians.lockAutoDispatchCandidates().stream()
				.filter(value -> !value.getId().equals(excludedTechnicianId))
				.filter(value -> skills(value).contains(order.getCategory()) || skills(value).contains("ALL"))
				.filter(value -> coversOrganization(value, order.getOrganizationId()))
				.filter(value -> coversArea(value, order.getLocation()))
				.filter(value -> isOnDuty(value, now))
				.findFirst().orElse(null);
		if (target == null) {
			order.setDispatchAttempts(order.getDispatchAttempts() + 1);
			order.setNextDispatchRetryAt(now.plusMinutes(retryMinutes(order.getUrgency())));
			orders.save(order);
			recordEvent(order, "AUTO_DISPATCH_FAILED", order.getStatus(), order.getStatus(), actor,
					"没有满足技能、校区、责任区域、值班状态和负载条件的维修人员，将按紧急程度自动重试");
			return;
		}
		assign(order, target, "AUTO", actor, "系统按技能、责任范围、值班和负载自动派单", now);
	}

	private void assign(RepairOrder order, MaintenanceTechnician target, String mode, String actor, String remark) {
		assign(order, target, mode, actor, remark, LocalDateTime.now());
	}

	private void assign(RepairOrder order, MaintenanceTechnician target, String mode, String actor, String remark,
			LocalDateTime now) {
		String old = order.getStatus();
		boolean sameTechnician = target.getId().equals(order.getTechnicianId());
		order.setDispatchMode(mode);
		order.setTechnicianId(target.getId());
		order.setTechnicianUsername(target.getUsername());
		order.setTechnicianName(target.getTechnicianName());
		order.setStatus("DISPATCHED");
		order.setDispatchedAt(now);
		order.setDispatchDeadline(now.plusMinutes(acceptanceMinutes(order.getUrgency())));
		order.setNextDispatchRetryAt(null);
		order.setDispatchAttempts(order.getDispatchAttempts() + 1);
		orders.save(order);
		if (!sameTechnician) {
			target.setCurrentLoad(target.getCurrentLoad() + 1);
			technicians.save(target);
		}
		recordEvent(order, mode + "_DISPATCH", old, "DISPATCHED", actor,
				or(remark, "派单给" + target.getTechnicianName()));
	}

	@Transactional
	public int processDispatchGovernance(LocalDateTime now, int batchSize) {
		Pageable batch = PageRequest.of(0, Math.max(1, Math.min(batchSize, 200)));
		List<RepairOrder> expired = orders.lockExpiredDispatches(now, batch);
		for (RepairOrder order : expired) {
			String previousTechnicianId = order.getTechnicianId();
			String previousTechnicianName = order.getTechnicianName();
			releaseTechnician(previousTechnicianId);
			order.setStatus("PENDING_DISPATCH");
			order.setTechnicianId(null);
			order.setTechnicianUsername(null);
			order.setTechnicianName(null);
			order.setDispatchDeadline(null);
			orders.save(order);
			recordEvent(order, "ACCEPT_TIMEOUT", "DISPATCHED", "PENDING_DISPATCH", "SYSTEM",
					"维修人员" + previousTechnicianName + "未在规定时间内接单，系统自动改派");
			autoDispatch(order, "SYSTEM", previousTechnicianId, now);
		}
		List<RepairOrder> pending = orders.lockPendingAutoDispatches(now, batch);
		for (RepairOrder order : pending) {
			autoDispatch(order, "SYSTEM", null, now);
		}
		return expired.size() + pending.size();
	}

	@Transactional
	public int reconcileTechnicianLoads() {
		int changed = 0;
		for (MaintenanceTechnician technician : technicians.findAll()) {
			int actual = Math.toIntExact(orders.countByTechnicianIdAndStatusIn(
					technician.getId(), ACTIVE_ORDER_STATUSES));
			if (!Integer.valueOf(actual).equals(technician.getCurrentLoad())) {
				technician.setCurrentLoad(actual);
				technicians.save(technician);
				changed++;
			}
		}
		return changed;
	}

	private void transition(RepairOrder order, Collection<String> allowed, String target, String type,
			String actor, String remark) {
		require(allowed.contains(order.getStatus()), "当前状态不能执行该操作");
		String old = order.getStatus();
		order.setStatus(target);
		recordEvent(order, type, old, target, actor, remark);
	}

	private void recordEvent(RepairOrder order, String type, String from, String to, String actor, String remark) {
		RepairOrderEvent event = new RepairOrderEvent();
		event.setRepairOrderId(order.getId());
		event.setEventType(type);
		event.setFromStatus(from);
		event.setToStatus(to);
		event.setOperatorUsername(actor);
		event.setRemark(remark);
		events.save(event);
	}

	private void releaseTechnician(String technicianId) {
		if (technicianId == null) return;
		technicians.findById(technicianId).ifPresent(value -> {
			value.setCurrentLoad(Math.max(0, value.getCurrentLoad() - 1));
			technicians.save(value);
		});
	}

	private void ledger(String orderId, String materialId, String type, BigDecimal quantity, BigDecimal amount,
			String actor, String description) {
		MaintenanceLedgerEntry entry = new MaintenanceLedgerEntry();
		entry.setRepairOrderId(orderId);
		entry.setMaterialId(materialId);
		entry.setEntryType(type);
		entry.setQuantity(quantity);
		entry.setAmount(amount);
		entry.setOperatorUsername(actor);
		entry.setDescription(description);
		ledger.save(entry);
	}

	private RepairOrderView view(RepairOrder value, boolean detailed) {
		return new RepairOrderView(value.getId(), value.getOrderNo(), value.getTitle(), value.getCategory(),
				value.getDescription(), value.getLocation(), value.getUrgency(), value.getContactName(),
				value.getContactPhone(), value.getReporterUsername(), value.getReporterName(), value.getOrganizationId(),
				value.getStatus(),
				value.getDispatchMode(), value.getTechnicianId(), value.getTechnicianUsername(), value.getTechnicianName(),
				value.getCreateTime(), value.getDispatchedAt(), value.getDispatchDeadline(), value.getDispatchAttempts(),
				value.getAcceptedAt(), value.getStartedAt(),
				value.getCompletedAt(), value.getClosedAt(), value.getCompletionNote(), value.getCloseComment(),
				value.getRating(),
				detailed ? fileViews(REPORT_FILE_TYPE, value.getId()) : List.of(),
				detailed ? fileViews(COMPLETION_FILE_TYPE, value.getId()) : List.of(),
				detailed ? events.findByRepairOrderIdOrderByCreateTime(value.getId()).stream()
						.map(com.chronos.facility.model.dto.FacilityDtos.RepairOrderEventView::from).toList() : List.of(),
				detailed ? requests.findByRepairOrderIdOrderByCreateTime(value.getId()).stream()
						.map(this::requestView).toList() : List.of());
	}

	private MaterialRequestView requestView(MaterialRequest value) {
		return new MaterialRequestView(value.getId(), value.getRequestNo(), value.getRepairOrderId(),
				value.getApplicantUsername(), value.getApplicantName(), value.getStatus(), value.getRemark(),
				value.getReviewedBy(), value.getReviewedAt(), value.getReviewComment(), value.getIssuedBy(),
				value.getIssuedAt(), value.getCreateTime(), requestItems.findByRequestIdOrderByCreateTime(value.getId())
						.stream().map(MaterialRequestItemView::from).toList());
	}

	private List<ManagedFileView> fileViews(String type, String id) {
		return files.findByBusinessTypeAndBusinessIdAndStatusOrderByCreateTime(type, id, "ACTIVE")
				.stream().map(ManagedFileView::from).toList();
	}

	private RepairOrder assigned(String id, String actor) {
		RepairOrder value = order(id);
		if (!actor.equals(value.getTechnicianUsername())) throw new AccessDeniedException("该工单未派给当前账号");
		return value;
	}

	private RepairOrder order(String id) {
		return orders.findById(id).orElseThrow(() -> new IllegalArgumentException("报修单不存在"));
	}

	private MaintenanceTechnician technician(String id) {
		return technicians.findById(id).orElseThrow(() -> new IllegalArgumentException("维修人员不存在"));
	}

	private MaintenanceMaterial material(String id) {
		return materials.findById(id).orElseThrow(() -> new IllegalArgumentException("物料不存在"));
	}

	private MaterialRequest request(String id) {
		return requests.findById(id).orElseThrow(() -> new IllegalArgumentException("物料申请不存在"));
	}

	private AdminUser account(String username) {
		AdminUser value = users.findByUsername(username);
		if (value == null || !Integer.valueOf(1).equals(value.getStatus())) {
			throw new AccessDeniedException("当前账号不可用");
		}
		return value;
	}

	private Pageable page(int page, int size) {
		return PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100),
				Sort.by(Sort.Direction.DESC, "createTime"));
	}

	private Set<String> skills(MaintenanceTechnician value) {
		return Arrays.stream(value.getSkillCategories().toUpperCase(Locale.ROOT).split(","))
				.map(String::trim).filter(item -> !item.isBlank()).collect(Collectors.toSet());
	}

	private boolean coversOrganization(MaintenanceTechnician technician, String organizationId) {
		Set<String> scopes = csv(technician.getResponsibilityOrganizationIds());
		return scopes.contains("ALL") || organizationId != null && scopes.contains(organizationId.toUpperCase(Locale.ROOT));
	}

	private boolean coversArea(MaintenanceTechnician technician, String location) {
		Set<String> areas = csv(technician.getResponsibilityAreas());
		if (areas.contains("ALL")) return true;
		if (location == null || location.isBlank()) return false;
		String normalizedLocation = location.toUpperCase(Locale.ROOT);
		return areas.stream().anyMatch(normalizedLocation::contains);
	}

	private boolean isOnDuty(MaintenanceTechnician technician, LocalDateTime now) {
		Set<String> days = csv(technician.getDutyDays());
		LocalTime start = technician.getDutyStartTime();
		LocalTime end = technician.getDutyEndTime();
		LocalTime time = now.toLocalTime();
		if (!start.isAfter(end)) {
			return includesDay(days, now.getDayOfWeek().name()) && !time.isBefore(start) && !time.isAfter(end);
		}
		if (!time.isBefore(start)) return includesDay(days, now.getDayOfWeek().name());
		return !time.isAfter(end) && includesDay(days, now.minusDays(1).getDayOfWeek().name());
	}

	private boolean includesDay(Set<String> days, String day) {
		return days.contains("ALL") || days.contains(day);
	}

	private Set<String> csv(String value) {
		if (value == null || value.isBlank()) return Set.of("ALL");
		return Arrays.stream(value.toUpperCase(Locale.ROOT).split(","))
				.map(String::trim).filter(item -> !item.isBlank()).collect(Collectors.toSet());
	}

	private String csvOrAll(String value) {
		if (value == null || value.isBlank()) return "ALL";
		return Arrays.stream(value.split(","))
				.map(String::trim).filter(item -> !item.isBlank()).distinct().collect(Collectors.joining(","));
	}

	private int acceptanceMinutes(String urgency) {
		return switch (urgency) {
			case "CRITICAL" -> Math.max(1, criticalAcceptanceMinutes);
			case "URGENT" -> Math.max(1, urgentAcceptanceMinutes);
			default -> Math.max(1, normalAcceptanceMinutes);
		};
	}

	private int retryMinutes(String urgency) {
		return switch (urgency) {
			case "CRITICAL" -> Math.max(1, criticalRetryMinutes);
			case "URGENT" -> Math.max(1, urgentRetryMinutes);
			default -> Math.max(1, normalRetryMinutes);
		};
	}

	private String number(String prefix, long sequence) {
		return prefix + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + String.format("%06d", sequence);
	}

	private String normalizeCategory(String value) {
		return normalize(value, Set.of("ELECTRICAL", "WATER", "NETWORK", "HVAC", "FURNITURE", "SECURITY", "OTHER"),
				null, "故障分类无效");
	}

	private String normalize(String value, Set<String> allowed, String fallback, String message) {
		String normalized = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
		require(normalized != null && allowed.contains(normalized), message);
		return normalized;
	}

	private BigDecimal positive(BigDecimal value, String message) {
		require(value != null && value.compareTo(BigDecimal.ZERO) > 0, message);
		return value;
	}

	private BigDecimal nonNegative(BigDecimal value, String message) {
		BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
		require(normalized.compareTo(BigDecimal.ZERO) >= 0, message);
		return normalized;
	}

	private String required(String value, String message) {
		requireText(value, message);
		return value.trim();
	}

	private void requireText(String value, String message) {
		require(value != null && !value.isBlank(), message);
	}

	private void require(boolean condition, String message) {
		if (!condition) throw new IllegalArgumentException(message);
	}

	private String limit(String value, int max) {
		if (value == null) return null;
		String normalized = value.trim();
		return normalized.length() <= max ? normalized : normalized.substring(0, max);
	}

	private String or(String... values) {
		for (String value : values) if (value != null && !value.isBlank()) return value.trim();
		return null;
	}

	private String suffix(String value) {
		return value == null || value.isBlank() ? "" : "，" + value.trim();
	}
}
