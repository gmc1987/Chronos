package com.chronos.education.operationagent.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IOrganizationRepository;
import com.chronos.education.scheduling.service.EducationResourceTransactionLock;
import com.chronos.education.scheduling.service.TeacherPersonnelWorkflowService;
import com.chronos.security.AdminUserDetailsService;
import com.chronos.workflow.WorkflowService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OperationAgentTeacherOnboardingTool {
	static final String CODE = "teacher.onboarding.request";
	private static final String FLOW_CODE = TeacherPersonnelWorkflowService.PREFIX + "ONBOARD";
	private static final String BUSINESS_KEY_PREFIX = "AGENT-TEACHER-ONBOARD-";

	private final TeacherPersonnelWorkflowService personnel;
	private final IOrganizationRepository organizations;
	private final WorkflowService workflows;
	private final EducationResourceTransactionLock locks;
	private final JdbcTemplate jdbc;
	private final AdminUserDetailsService userDetails;

	@Transactional
	public Map<String, Object> execute(Map<String, Object> config, String actor) {
		var user = userDetails.loadUserByUsername(actor);
		if (!user.isEnabled() || !user.isAccountNonLocked()) {
			throw new AccessDeniedException("申请人账号已停用或锁定");
		}
		// The Agent worker only carries a username. Apply the same current IAM
		// authorities as the human workflow entry point before evaluating the request.
		var previousContext = SecurityContextHolder.getContext();
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(actor, null, user.getAuthorities()));
		SecurityContextHolder.setContext(context);
		try {
			return prepareOrSubmit(config, actor);
		} finally {
			SecurityContextHolder.setContext(previousContext);
		}
	}

	private Map<String, Object> prepareOrSubmit(Map<String, Object> config, String actor) {
		boolean dryRun = Boolean.TRUE.equals(config.get("dryRun"));
		String organizationId = required(config, "organizationId", "目标学校");
		var school = organizations.findById(organizationId)
				.orElseThrow(() -> new IllegalArgumentException("目标学校不存在"));
		String effectiveDate;
		try {
			effectiveDate = LocalDate.parse(required(config, "effectiveDate", "生效日期")).toString();
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException("入职生效日期须使用 YYYY-MM-DD 格式");
		}
		String reason = required(config, "reason", "入职申请说明");
		List<String> employeeCodes = employeeCodes(config.get("employeeCodes"));
		String businessKey = dryRun ? "" : BUSINESS_KEY_PREFIX + required(config, "__stepRunId", "执行步骤");
		if (!dryRun) {
			// The workflow may already have committed when the Agent retries a step.
			locks.lockSemester(businessKey);
			var submitted = submittedCases(businessKey, actor);
			if (!submitted.isEmpty()) return submittedResult(submitted);
		}

		var rows = new ArrayList<Map<String, Object>>();
		var errors = new ArrayList<String>();
		var forms = new ArrayList<Map<String, Object>>();
		for (String employeeCode : employeeCodes) {
			Map<String, Object> form = Map.of("employeeCode", employeeCode, "organizationCode", school.getOrgCode(),
					"effectiveDate", effectiveDate, "reason", reason);
			try {
				String name = personnel.previewOnboarding(form, actor);
				rows.add(Map.of("employeeCode", employeeCode, "employeeName", name,
						"organizationName", school.getOrganizationName(), "effectiveDate", effectiveDate,
						"reason", reason, "status", "待提交"));
				forms.add(form);
			} catch (IllegalArgumentException | IllegalStateException exception) {
				errors.add(employeeCode + "：" + exception.getMessage());
			}
		}
		if (dryRun) {
			return Map.of("status", "DRAFT", "dryRun", true, "rows", rows, "errors", errors,
					"message", errors.isEmpty() ? "入职申请草稿已生成，确认后提交独立审批" : "入职申请资料待补齐");
		}
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException(String.join("；", errors));
		}
		for (var form : forms) {
			workflows.startByCode(FLOW_CODE, businessKey, form, actor);
		}
		return submittedResult(submittedCases(businessKey, actor));
	}

	List<Map<String, Object>> casesForStep(String stepId, String applicant) {
		return submittedCases(BUSINESS_KEY_PREFIX + stepId, applicant);
	}

	private List<Map<String, Object>> submittedCases(String businessKey, String actor) {
		return jdbc.queryForList("""
				select c.instance_id as "instanceId", c.employee_code as "employeeCode",
				       c.employee_name as "employeeName", to_char(c.effective_date, 'YYYY-MM-DD') as "effectiveDate",
				       case when c.status='REVIEWING' and w.status in ('CANCELLED','TERMINATED','WITHDRAWN')
				            then 'CANCELLED' else c.status end as status,
				       c.error_message as "errorMessage"
				from edu_teacher_personnel_case c join wf_instance w on w.id=c.instance_id
				where w.business_key=? and c.applicant=?
				order by c.employee_code
				""", businessKey, actor);
	}

	private Map<String, Object> submittedResult(List<Map<String, Object>> rows) {
		return Map.of("status", "SUBMITTED", "dryRun", false, "rows", rows,
				"message", "入职申请已提交。请在教师人事工作台查看审批和生效状态。");
	}

	private List<String> employeeCodes(Object value) {
		if (!(value instanceof List<?> codes) || codes.isEmpty() || codes.size() > 50
				|| codes.stream().anyMatch(code -> !(code instanceof String text) || text.isBlank())) {
			throw new IllegalArgumentException("请提供 1 至 50 个已登记教职工的工号");
		}
		return codes.stream().map(code -> ((String) code).strip()).distinct().sorted().toList();
	}

	private String required(Map<String, Object> config, String key, String label) {
		if (!(config.get(key) instanceof String value) || value.isBlank()) {
			throw new IllegalArgumentException("请补充" + label);
		}
		return value.strip();
	}
}
