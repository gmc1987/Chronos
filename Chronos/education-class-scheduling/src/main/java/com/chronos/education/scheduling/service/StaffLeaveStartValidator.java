package com.chronos.education.scheduling.service;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.workflow.WorkflowStartValidator;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/** 防止通用流程入口绕过教职工身份和假期余额校验。 */
@Component
public class StaffLeaveStartValidator implements WorkflowStartValidator {
	private static final String FLOW_CODE = "EDU_TEACHER_LEAVE_APPROVAL";
	private final IAdminUserRepository users;
	private final IEmployeeRepository employees;
	private final EducationUserBindingRepository bindings;
	private final StaffLeaveBalanceService balances;

	public StaffLeaveStartValidator(
			IAdminUserRepository users,
			IEmployeeRepository employees,
			EducationUserBindingRepository bindings,
			StaffLeaveBalanceService balances) {
		this.users = users;
		this.employees = employees;
		this.bindings = bindings;
		this.balances = balances;
	}

	@Override
	public boolean supports(String flowCode) {
		return FLOW_CODE.equals(flowCode);
	}

	@Override
	public void validate(String actor, Map<String, Object> formData) {
		AdminUser account = users.findByUsername(actor);
		if (account != null && account.getEmployeeId() != null
				&& employees.findById(account.getEmployeeId())
						.filter(employee -> "ACTIVE".equals(employee.getEmploymentStatus()))
						.isPresent()) {
			String type = required(formData, "leaveType", "请假类型不能为空");
			LocalDate start = date(formData, "startDate", "开始日期格式应为 yyyy-MM-dd");
			LocalDate end = date(formData, "endDate", "结束日期格式应为 yyyy-MM-dd");
			if (end.isBefore(start)) throw new IllegalArgumentException("结束日期不能早于开始日期");
			required(formData, "reason", "请假原因不能为空");
			balances.assertAvailable(account.getEmployeeId(), type, start, end);
			return;
		}
		boolean teacher = bindings.findByUsernameAndProfileTypeAndStatus(actor, "TEACHER", "ACTIVE").isPresent();
		if (!teacher) throw new AccessDeniedException("只有有效教职工可以发起教职工请假");
	}

	private LocalDate date(Map<String, Object> formData, String key, String message) {
		try {
			return LocalDate.parse(required(formData, key, message));
		} catch (RuntimeException exception) {
			throw new IllegalArgumentException(message);
		}
	}

	private String required(Map<String, Object> formData, String key, String message) {
		Object value = formData == null ? null : formData.get(key);
		if (value == null || String.valueOf(value).isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return String.valueOf(value).trim();
	}
}
