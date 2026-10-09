package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.dao.BusinessTripConfigurationRepository;
import com.chronos.education.scheduling.dao.BusinessTripRequestRepository;
import com.chronos.education.scheduling.model.BusinessTripConfiguration;
import com.chronos.education.scheduling.model.BusinessTripRequest;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.workflow.WorkflowInstance;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.workflow.WorkflowService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 教职工出差申请、流程、财务协同与考勤查询。 */
@Service
public class BusinessTripService {
	public static final String FLOW_CODE = "STAFF_BUSINESS_TRIP_APPROVAL";
	private final BusinessTripRequestRepository trips;
	private final BusinessTripConfigurationRepository configurations;
	private final IAdminUserRepository users;
	private final IEmployeeRepository employees;
	private final WorkflowService workflows;
	private final IAuditLogService audit;
	private final List<FinanceIntegrationGateway> financeGateways;

	public BusinessTripService(
			BusinessTripRequestRepository trips,
			BusinessTripConfigurationRepository configurations,
			IAdminUserRepository users,
			IEmployeeRepository employees,
			WorkflowService workflows,
			IAuditLogService audit,
			List<FinanceIntegrationGateway> financeGateways) {
		this.trips = trips;
		this.configurations = configurations;
		this.users = users;
		this.employees = employees;
		this.workflows = workflows;
		this.audit = audit;
		this.financeGateways = financeGateways;
	}

	@Transactional
	public BusinessTripRequest start(String username, Map<String, Object> command) {
		String employeeId = requireEmployee(username);
		String destination = required(command, "destination", "出差地点不能为空");
		LocalDate startDate = date(command, "startDate", "开始日期格式不正确");
		LocalDate endDate = date(command, "endDate", "结束日期格式不正确");
		if (endDate.isBefore(startDate)) throw new IllegalArgumentException("结束日期不能早于开始日期");
		String purpose = required(command, "purpose", "出差事由不能为空");
		String currency = optional(command, "currency", "CNY").toUpperCase();
		if (!currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("币种代码必须为三位大写字母");
		BigDecimal transport = amount(command, "transportAmount");
		BigDecimal accommodation = amount(command, "accommodationAmount");
		BigDecimal meal = amount(command, "mealAmount");
		BigDecimal other = amount(command, "otherAmount");
		BigDecimal estimated = transport.add(accommodation).add(meal).add(other);
		String businessKey = "BUSINESS_TRIP:" + UUID.randomUUID();
		BusinessTripConfiguration config = configuration();
		FinanceIntegrationGateway gateway = activeGateway();
		if (Boolean.TRUE.equals(config.getFinanceRequired()) && gateway == null) {
			throw new IllegalStateException("出差流程已要求对接财务模块，但完整财务模块尚未接入，流程不能继续");
		}
		String projectCode = optional(command, "budgetProjectCode", null);
		String costCenter = optional(command, "costCenterCode", null);
		if (Boolean.TRUE.equals(config.getFinanceRequired())
				&& (projectCode == null || costCenter == null)) {
			throw new IllegalArgumentException("启用财务强制校验后必须填写预算项目和成本中心");
		}
		Map<String, Object> form = new LinkedHashMap<>();
		form.put("integrationBusinessKey", businessKey);
		form.put("destination", destination);
		form.put("startDate", startDate.toString());
		form.put("endDate", endDate.toString());
		form.put("purpose", purpose);
		form.put("budgetProjectCode", projectCode == null ? "" : projectCode);
		form.put("costCenterCode", costCenter == null ? "" : costCenter);
		form.put("currency", currency);
		form.put("transportAmount", transport);
		form.put("accommodationAmount", accommodation);
		form.put("mealAmount", meal);
		form.put("otherAmount", other);
		form.put("estimatedAmount", estimated);
		WorkflowInstance workflow = workflows.startByCode(FLOW_CODE, businessKey, form, username);

		BusinessTripRequest trip = new BusinessTripRequest();
		trip.setWorkflowInstanceId(workflow.getId());
		trip.setBusinessKey(businessKey);
		trip.setEmployeeId(employeeId);
		trip.setDestination(destination);
		trip.setStartDate(startDate);
		trip.setEndDate(endDate);
		trip.setPurpose(purpose);
		trip.setBudgetProjectCode(projectCode);
		trip.setCostCenterCode(costCenter);
		trip.setCurrency(currency);
		trip.setTransportAmount(transport);
		trip.setAccommodationAmount(accommodation);
		trip.setMealAmount(meal);
		trip.setOtherAmount(other);
		trip.setEstimatedAmount(estimated);
		trip.setFinanceStatus(gateway == null ? "NOT_REQUIRED" : "PRECHECKED");
		trips.save(trip);
		audit.log(username, "BUSINESS_TRIP_SUBMIT", "workflowInstanceId=" + workflow.getId());
		return trip;
	}

	@Transactional(readOnly = true)
	public List<BusinessTripRequest> mine(String username) {
		return trips.findByEmployeeIdOrderByStartDateDesc(requireEmployee(username));
	}

	@Transactional
	public BusinessTripRequest withdraw(String username, String id, String reason) {
		BusinessTripRequest trip = owned(username, id);
		if (!"PENDING".equals(trip.getStatus())) throw new IllegalStateException("只有审批中的出差申请可以撤回");
		workflows.withdraw(trip.getWorkflowInstanceId(), reason, username);
		trip.setStatus("WITHDRAWN");
		trip.setWithdrawnBy(username);
		trip.setWithdrawnAt(LocalDateTime.now());
		audit.log(username, "BUSINESS_TRIP_WITHDRAW", "tripId=" + id);
		return trips.save(trip);
	}

	@Transactional
	public BusinessTripRequest cancel(String username, String id, String reason) {
		BusinessTripRequest trip = owned(username, id);
		if (!"APPROVED".equals(trip.getStatus())) throw new IllegalStateException("只有已批准的出差可以取消");
		String requiredReason = reason == null || reason.isBlank() ? null : reason.trim();
		if (requiredReason == null) throw new IllegalArgumentException("取消原因不能为空");
		FinanceIntegrationGateway gateway = activeGateway();
		if ("RESERVED".equals(trip.getFinanceStatus())) {
			if (gateway == null) throw new IllegalStateException("财务预算已占用，但财务接口当前不可用，不能取消");
			FinanceIntegrationGateway.FinanceOperationResult released =
					gateway.release(trip.getBusinessKey(), trip.getFinanceReference(), requiredReason);
			if (!released.success()) throw new IllegalStateException("财务预算释放失败：" + released.message());
			trip.setFinanceStatus("RELEASED");
		}
		trip.setStatus("CANCELLED");
		trip.setCancelReason(requiredReason);
		trip.setCancelledBy(username);
		trip.setCancelledAt(LocalDateTime.now());
		audit.log(username, "BUSINESS_TRIP_CANCEL", "tripId=" + id);
		return trips.save(trip);
	}

	@Transactional
	public BusinessTripConfiguration updateConfiguration(boolean required, String username) {
		BusinessTripConfiguration value = configuration();
		value.setFinanceRequired(required);
		audit.log(username, "BUSINESS_TRIP_FINANCE_REQUIREMENT_UPDATE", "required=" + required);
		return configurations.save(value);
	}

	@Transactional
	public Map<String, Object> integrationStatus() {
		BusinessTripConfiguration config = configuration();
		FinanceIntegrationGateway gateway = activeGateway();
		Map<String, Object> status = new LinkedHashMap<>();
		status.put("financeRequired", Boolean.TRUE.equals(config.getFinanceRequired()));
		status.put("financeConnected", gateway != null);
		status.put("providerCode", gateway == null ? null : gateway.providerCode());
		status.put("submissionAllowed", !Boolean.TRUE.equals(config.getFinanceRequired()) || gateway != null);
		status.put("message", Boolean.TRUE.equals(config.getFinanceRequired()) && gateway == null
				? "已要求对接财务模块，但完整财务模块尚未接入，出差流程当前不可发起"
				: gateway == null ? "财务模块未接入，当前按非财务模式运行" : "财务模块已接入");
		return status;
	}

	@Transactional(readOnly = true)
	public List<BusinessTripRequest> approvedForAttendance(
			String employeeId, LocalDate startDate, LocalDate endDate) {
		if (endDate.isBefore(startDate)) throw new IllegalArgumentException("查询日期范围不正确");
		return trips.findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
				employeeId, "APPROVED", endDate, startDate);
	}

	@Transactional
	public void validateWorkflowStart(String username, Map<String, Object> form) {
		String employeeId = requireEmployee(username);
		String destination = required(form, "destination", "出差地点不能为空");
		LocalDate start = date(form, "startDate", "开始日期格式不正确");
		LocalDate end = date(form, "endDate", "结束日期格式不正确");
		if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不能早于开始日期");
		required(form, "purpose", "出差事由不能为空");
		String currency = optional(form, "currency", "CNY").toUpperCase();
		BigDecimal total = amount(form, "transportAmount")
				.add(amount(form, "accommodationAmount"))
				.add(amount(form, "mealAmount"))
				.add(amount(form, "otherAmount"));
		BusinessTripConfiguration config = configuration();
		FinanceIntegrationGateway gateway = activeGateway();
		if (Boolean.TRUE.equals(config.getFinanceRequired()) && gateway == null) {
			throw new IllegalStateException("出差流程已要求对接财务模块，但完整财务模块尚未接入，流程不能继续");
		}
		String projectCode = optional(form, "budgetProjectCode", null);
		String costCenter = optional(form, "costCenterCode", null);
		if (Boolean.TRUE.equals(config.getFinanceRequired())
				&& (projectCode == null || costCenter == null)) {
			throw new IllegalArgumentException("启用财务强制校验后必须填写预算项目和成本中心");
		}
		if (gateway != null) {
			String businessKey = optional(
					form, "integrationBusinessKey",
					"BUSINESS_TRIP_PRECHECK:" + username + ":" + start + ":" + end + ":" + destination);
			FinanceIntegrationGateway.FinanceOperationResult check = gateway.precheck(
					budgetRequest(businessKey, employeeId, projectCode, costCenter, total, currency));
			if (!check.success()) throw new IllegalStateException("财务预算预校验未通过：" + check.message());
		}
	}

	@Transactional
	void approve(
			String instanceId,
			String businessKey,
			String initiatedBy,
			String approver,
			Map<String, Object> form) {
		BusinessTripRequest trip = trips.findByWorkflowInstanceId(instanceId)
				.orElseGet(() -> createFromWorkflow(instanceId, businessKey, initiatedBy, form));
		if (!"PENDING".equals(trip.getStatus())) return;
		FinanceIntegrationGateway gateway = activeGateway();
		if (Boolean.TRUE.equals(configuration().getFinanceRequired()) && gateway == null) {
			throw new IllegalStateException("审批完成时财务模块不可用，预算无法冻结");
		}
		if (gateway != null) {
			FinanceIntegrationGateway.FinanceOperationResult reserved = gateway.reserve(budgetRequest(
					trip.getBusinessKey(), trip.getEmployeeId(), trip.getBudgetProjectCode(),
					trip.getCostCenterCode(), trip.getEstimatedAmount(), trip.getCurrency()));
			if (!reserved.success()) throw new IllegalStateException("财务预算冻结失败：" + reserved.message());
			trip.setFinanceStatus("RESERVED");
			trip.setFinanceReference(reserved.reference());
		}
		trip.setStatus("APPROVED");
		trip.setApprovedBy(approver);
		trips.save(trip);
	}

	@Transactional
	void reject(String instanceId) {
		trips.findByWorkflowInstanceId(instanceId).ifPresent(trip -> {
			if ("PENDING".equals(trip.getStatus())) {
				trip.setStatus("REJECTED");
				trips.save(trip);
			}
		});
	}

	private BusinessTripRequest createFromWorkflow(
			String instanceId, String businessKey, String initiatedBy, Map<String, Object> form) {
		BusinessTripRequest trip = new BusinessTripRequest();
		trip.setWorkflowInstanceId(instanceId);
		trip.setBusinessKey(businessKey);
		trip.setEmployeeId(requireEmployee(initiatedBy));
		trip.setDestination(required(form, "destination", "出差地点不能为空"));
		trip.setStartDate(date(form, "startDate", "开始日期格式不正确"));
		trip.setEndDate(date(form, "endDate", "结束日期格式不正确"));
		trip.setPurpose(required(form, "purpose", "出差事由不能为空"));
		trip.setBudgetProjectCode(optional(form, "budgetProjectCode", null));
		trip.setCostCenterCode(optional(form, "costCenterCode", null));
		trip.setCurrency(optional(form, "currency", "CNY").toUpperCase());
		trip.setTransportAmount(amount(form, "transportAmount"));
		trip.setAccommodationAmount(amount(form, "accommodationAmount"));
		trip.setMealAmount(amount(form, "mealAmount"));
		trip.setOtherAmount(amount(form, "otherAmount"));
		trip.setEstimatedAmount(trip.getTransportAmount()
				.add(trip.getAccommodationAmount())
				.add(trip.getMealAmount())
				.add(trip.getOtherAmount()));
		trip.setFinanceStatus(activeGateway() == null ? "NOT_REQUIRED" : "PRECHECKED");
		return trips.save(trip);
	}

	private FinanceIntegrationGateway.BudgetRequest budgetRequest(
			String businessKey, String employeeId, String projectCode, String costCenter,
			BigDecimal amount, String currency) {
		return new FinanceIntegrationGateway.BudgetRequest(
				businessKey, employeeId, projectCode, costCenter, amount, currency);
	}

	private BusinessTripConfiguration configuration() {
		return configurations.findByConfigCode("DEFAULT").orElseGet(() -> {
			BusinessTripConfiguration value = new BusinessTripConfiguration();
			value.setConfigCode("DEFAULT");
			value.setFinanceRequired(false);
			return configurations.saveAndFlush(value);
		});
	}

	private FinanceIntegrationGateway activeGateway() {
		return financeGateways.stream().filter(FinanceIntegrationGateway::available).findFirst().orElse(null);
	}

	private BusinessTripRequest owned(String username, String id) {
		String employeeId = requireEmployee(username);
		BusinessTripRequest trip = trips.findLockedById(id)
				.orElseThrow(() -> new IllegalArgumentException("出差申请不存在"));
		if (!employeeId.equals(trip.getEmployeeId())) throw new AccessDeniedException("只能操作本人的出差申请");
		return trip;
	}

	private String requireEmployee(String username) {
		AdminUser user = users.findByUsername(username);
		if (user == null || user.getEmployeeId() == null) {
			throw new AccessDeniedException("当前账号未绑定教职工档案");
		}
		return employees.findById(user.getEmployeeId())
				.filter(employee -> "ACTIVE".equals(employee.getEmploymentStatus()))
				.map(employee -> employee.getId())
				.orElseThrow(() -> new AccessDeniedException("当前账号未绑定有效教职工档案"));
	}

	private String required(Map<String, Object> values, String key, String message) {
		Object value = values == null ? null : values.get(key);
		if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException(message);
		return String.valueOf(value).trim();
	}

	private String optional(Map<String, Object> values, String key, String fallback) {
		Object value = values == null ? null : values.get(key);
		return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value).trim();
	}

	private LocalDate date(Map<String, Object> values, String key, String message) {
		try {
			return LocalDate.parse(required(values, key, message));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException(message);
		}
	}

	private BigDecimal amount(Map<String, Object> values, String key) {
		try {
			BigDecimal value = new BigDecimal(optional(values, key, "0"));
			if (value.signum() < 0) throw new IllegalArgumentException(key + "不能小于 0");
			return value.setScale(2, java.math.RoundingMode.HALF_UP);
		} catch (NumberFormatException exception) {
			throw new IllegalArgumentException(key + "格式不正确");
		}
	}
}
