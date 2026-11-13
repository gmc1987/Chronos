package com.chronos.education.scheduling.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.TeacherAcademicProfileRepository;
import com.chronos.education.scheduling.model.dto.TeacherEmploymentChangeCommand;
import com.chronos.security.DirectoryDataScopeGuard;
import com.chronos.security.IamAuthorization;
import com.chronos.service.iService.IRefreshTokenService;
import com.chronos.service.iService.IDataScopeService;
import com.chronos.service.impl.UserRoleAssignmentService;
import com.chronos.workflow.event.WorkflowCompletedEvent;
import com.chronos.workflow.event.WorkflowRejectedEvent;
import com.chronos.workflow.event.WorkflowStartedEvent;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeacherPersonnelWorkflowService {
	public static final String PREFIX = "EDU_TEACHER_PERSONNEL_";
	private static final Set<String> TYPES = Set.of("ONBOARD", "TERMINATE", "TRANSFER", "SUSPEND", "RESUME");
	private final JdbcTemplate jdbc;
	private final IEmployeeRepository employees;
	private final IEmployeeAssignmentRepository assignments;
	private final IOrganizationRepository organizations;
	private final IAdminUserRepository users;
	private final IRoleRepository roles;
	private final IDataScopeService dataScopes;
	private final TeacherAcademicProfileRepository teachers;
	private final EducationUserBindingRepository bindings;
	private final IRefreshTokenService refreshTokens;
	private final UserRoleAssignmentService roleAssignments;
	private final IamAuthorization authorization;
	private final DirectoryDataScopeGuard directory;
	private final AcademicDataService academic;
	private final TeacherEmploymentChangeService employment;
	private final EducationResourceTransactionLock locks;

	@EventListener
	public void started(WorkflowStartedEvent event) {
		String type = type(event.flowCode());
		if (type == null)
			return;
		var item = prepare(type, event.mainFormData(), event.actor(), true);
		event.runtimeVariables().put("personnelReviewers", item.reviewers());
		jdbc.update("""
				insert into edu_teacher_personnel_case
				(instance_id, employee_id, employee_code, employee_name, organization_id, target_organization_id,
				 change_type, effective_date, reason, applicant)
				values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", event.instanceId(), item.employeeId(), item.employeeCode(), item.employeeName(),
				item.organizationId(), item.targetOrganizationId(), type, item.effectiveDate(), item.reason(),
				event.actor());
	}

	/**
	 * Reuse the workflow's eligibility rules without creating a case or reserving a
	 * reviewer.
	 */
	public String previewOnboarding(Map<String, Object> form, String username) {
		return prepare("ONBOARD", form, username, false).employeeName();
	}

	private PreparedCase prepare(String type, Map<String, Object> form, String username, boolean lock) {
		var actor = authentication(username);
		var date = LocalDate.parse(required(form, "effectiveDate", "生效日期"));
		String reason = required(form, "reason", "申请说明");
		String employeeCode = text(form, "employeeCode");
		var account = users.findByUsername(username);
		var employee = (employeeCode.isEmpty()
				? employees.findById(account == null || account.getEmployeeId() == null ? "" : account.getEmployeeId())
				: employees.findByEmployeeCode(employeeCode))
				.orElseThrow(() -> new IllegalArgumentException("请填写已登记的教职工工号"));
		boolean self = account != null && employee.getId().equals(account.getEmployeeId());
		boolean manager = authorization.any(actor, "education:teacher:business:update",
				"education:teacher:business:manage");
		if (!manager && !(self && "TERMINATE".equals(type)))
			throw new AccessDeniedException("该业务须由人事发起");
		if (lock)
			locks.lockSemester("teacher-personnel:" + employee.getId());
		LocalDate sourceDate = "ONBOARD".equals(type) ? date : LocalDate.now();
		String orgCode = text(form, "organizationCode");
		String organization = orgCode.isEmpty() ? assignments.findCurrentPrimaryAssignment(employee.getId(), sourceDate)
				.map(item -> item.getOrganizationId())
				.orElseThrow(() -> new IllegalArgumentException("请填写学校编码，或先登记在生效日有效的主聘任职")) : school(orgCode);
		directory.requireOrganization(actor, organization);
		if (assignments.findCurrentAssignments(employee.getId(), sourceDate).stream()
				.noneMatch(item -> organization.equals(item.getOrganizationId()))) {
			throw new IllegalArgumentException("该教职工在所选学校没有对应的有效任职");
		}
		var teacher = teachers.findByEmployeeId(employee.getId());
		if ("ONBOARD".equals(type) == teacher.isPresent()) {
			throw new IllegalArgumentException("ONBOARD".equals(type) ? "该员工已有教师档案" : "该员工尚未建立教师档案");
		}
		if (teacher.isPresent() && teacher.get().getDepartmentId() != null
				&& !organization.equals(teacher.get().getDepartmentId())) {
			throw new IllegalArgumentException("所选学校与教师档案归属不一致，请核对任职");
		}
		String target = null;
		if ("TRANSFER".equals(type)) {
			target = school(required(form, "targetOrganizationCode", "调入学校编码"));
			directory.requireOrganization(actor, target);
			if (organization.equals(target))
				throw new IllegalArgumentException("调入学校必须与当前学校不同");
		}
		if (Boolean.TRUE.equals(jdbc.queryForObject(
				"""
						select exists(select 1 from edu_teacher_personnel_case
						where employee_id = ? and status in ('REVIEWING','APPROVED','APPLY_FAILED')
						and not exists (select 1 from wf_instance w where w.id=instance_id and w.status in ('CANCELLED','TERMINATED','WITHDRAWN')))
						""",
				Boolean.class, employee.getId()))) {
			throw new IllegalStateException("该教职工已有办理中的人事申请");
		}
		return new PreparedCase(employee.getId(), employee.getEmployeeCode(), employee.getEmployeeName(), organization,
				target, date, reason, reviewers(organization, target, username, employee.getId()));
	}

	private record PreparedCase(String employeeId, String employeeCode, String employeeName, String organizationId,
			String targetOrganizationId, LocalDate effectiveDate, String reason, List<String> reviewers) {
	}

	@EventListener
	public void completed(WorkflowCompletedEvent event) {
		if (type(event.flowCode()) == null)
			return;
		var item = detail(event.instanceId());
		if (!"REVIEWING".equals(item.status()))
			return;
		var actor = authentication(event.completedBy());
		assertIndependentReviewer(item, actor.getName());
		directory.requireOrganization(actor, item.organizationId());
		if (item.targetOrganizationId() != null)
			directory.requireOrganization(actor, item.targetOrganizationId());
		jdbc.update("""
				update edu_teacher_personnel_case set status='APPROVED', reviewer=?, error_message=null
				where instance_id=? and status='REVIEWING'
				""", actor.getName(), event.instanceId());
	}

	@EventListener
	public void rejected(WorkflowRejectedEvent event) {
		if (type(event.flowCode()) != null) {
			var item = detail(event.instanceId());
			var actor = authentication(event.rejectedBy());
			assertIndependentReviewer(item, actor.getName());
			directory.requireOrganization(actor, item.organizationId());
			if (item.targetOrganizationId() != null)
				directory.requireOrganization(actor, item.targetOrganizationId());
			jdbc.update("""
					update edu_teacher_personnel_case set status='REJECTED', reviewer=?
					where instance_id=? and status='REVIEWING'
					""", event.rejectedBy(), event.instanceId());
		}
	}

	@Transactional
	public void apply(String id) {
		var snapshot = detail(id);
		locks.lockSemester("teacher-personnel:" + snapshot.employeeId());
		var item = detail(id);
		if ("APPLIED".equals(item.status()))
			return;
		if (!Set.of("APPROVED", "APPLY_FAILED").contains(item.status())
				|| item.effectiveDate().isAfter(LocalDate.now())) {
			throw new IllegalStateException("申请尚未批准或尚未到生效日");
		}
		var employee = employees.findById(item.employeeId()).orElseThrow();
		if ("ONBOARD".equals(item.changeType())) {
			boolean assigned = assignments.findCurrentAssignments(item.employeeId(), LocalDate.now()).stream()
					.anyMatch(value -> item.organizationId().equals(value.getOrganizationId()));
			if (!assigned || !"ACTIVE".equals(employee.getEmploymentStatus())) {
				throw new IllegalStateException("请先确认教职工在岗且该学校任职已生效");
			}
			academic.createApprovedTeacher(item.employeeId(), item.organizationId());
		} else {
			var teacher = teachers.findByEmployeeId(item.employeeId()).orElseThrow();
			if (Set.of("TRANSFER", "TERMINATE", "SUSPEND").contains(item.changeType())) {
				assertAdditionalHandover(item.employeeId(), teacher.getId());
			}
			if ("TERMINATE".equals(item.changeType()) && assignments.findByEmployeeIdAndStatus(item.employeeId(), 1)
					.stream().anyMatch(value -> !item.organizationId().equals(value.getOrganizationId())
							&& (value.getEffectiveTo() == null || !value.getEffectiveTo().isBefore(LocalDate.now())))) {
				throw new IllegalStateException("教师还有其他学校的有效或待生效任职，请先办理主聘调动");
			}
			if ("TRANSFER".equals(item.changeType()))
				transferPrimaryAssignment(item);
			employment.register(teacher.getId(), new TeacherEmploymentChangeCommand(item.changeType(),
					item.targetOrganizationId(), item.effectiveDate(), item.reason()), item.reviewer());
			if ("TERMINATE".equals(item.changeType()))
				terminateEmployee(item);
		}
		jdbc.update("""
				update edu_teacher_personnel_case set status='APPLIED', applied_at=current_timestamp, error_message=null
				where instance_id=?
				""", id);
	}

	private void assertAdditionalHandover(String employeeId, String teacherId) {
		if (Boolean.TRUE.equals(jdbc.queryForObject("""
				select exists(select 1 from edu_exam_invigilation i
				join edu_exam_room r on r.id=i.room_id join edu_exam_session s on s.id=r.session_id
				where i.teacher_id=? and i.status in ('ASSIGNED','STANDBY') and s.exam_date>=current_date
				  and s.status not in ('CANCELLED','COMPLETED'))
				""", Boolean.class, teacherId))) {
			throw new IllegalStateException("仍有未交接的监考安排");
		}
		users.findByEmployeeId(employeeId).ifPresent(account -> {
			if (Boolean.TRUE.equals(jdbc.queryForObject("""
					select exists(select 1 from wf_task where assignee=? and status='PENDING')
					""", Boolean.class, account.getUsername()))) {
				throw new IllegalStateException("仍有未交接的流程待办");
			}
		});
	}

	private void transferPrimaryAssignment(Case item) {
		var active = assignments.findCurrentAssignments(item.employeeId(), LocalDate.now());
		var target = active.stream().filter(value -> item.targetOrganizationId().equals(value.getOrganizationId()))
				.toList();
		if (target.size() != 1) {
			throw new IllegalStateException("请先在调入学校维护唯一一条有效任职");
		}
		var current = active.stream().filter(value -> Boolean.TRUE.equals(value.getPrimaryAssignment())).toList();
		if (current.size() > 1
				|| current.stream().anyMatch(value -> !item.organizationId().equals(value.getOrganizationId())
						&& !item.targetOrganizationId().equals(value.getOrganizationId()))) {
			throw new IllegalStateException("当前主聘任职与申请不一致，请先核对任职");
		}
		for (var source : current) {
			if (!source.getId().equals(target.getFirst().getId())) {
				source.setPrimaryAssignment(false);
				source.setStatus(0);
				source.setEffectiveTo(item.effectiveDate());
				assignments.save(source);
			}
		}
		var next = target.getFirst();
		next.setPrimaryAssignment(true);
		assignments.saveAndFlush(next);
	}

	private void terminateEmployee(Case item) {
		for (var assignment : assignments.findByEmployeeIdAndStatus(item.employeeId(), 1)) {
			if (item.organizationId().equals(assignment.getOrganizationId())) {
				assignment.setStatus(0);
				if (assignment.getEffectiveFrom() == null
						|| !assignment.getEffectiveFrom().isAfter(item.effectiveDate())) {
					assignment.setEffectiveTo(item.effectiveDate());
				}
				assignments.save(assignment);
			}
		}
		var employee = employees.findById(item.employeeId()).orElseThrow();
		employee.setEmploymentStatus("TERMINATED");
		employee.setLeaveDate(item.effectiveDate());
		employees.save(employee);
		users.findByEmployeeId(item.employeeId()).ifPresent(account -> {
			roleAssignments.removeManualRoleSource(account, "TEACHER_PROVISIONING");
			boolean otherIdentity = bindings.findByUsernameAndStatusOrderByProfileType(account.getUsername(), "ACTIVE")
					.stream().anyMatch(binding -> !"TEACHER".equals(binding.getProfileType()));
			if (!otherIdentity) {
				account.setStatus(0);
				account.setAccountLocked(true);
			}
			account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
			users.save(account);
			refreshTokens.revokeAll(account.getUsername());
		});
	}

	public List<String> due() {
		return jdbc.queryForList("""
				select instance_id from edu_teacher_personnel_case
				where status='APPROVED' and effective_date<=current_date order by effective_date limit 100
				""", String.class);
	}

	public void failed(String id, String message) {
		jdbc.update("""
				update edu_teacher_personnel_case set status='APPLY_FAILED', error_message=?
				where instance_id=? and status in ('APPROVED','APPLY_FAILED')
				""", message, id);
	}

	public List<Map<String, Object>> list(String organization, String applicant, String status, int page, int size) {
		return jdbc.queryForList(
				"""
						select c.instance_id as "instanceId", c.employee_code as "employeeCode", c.employee_name as "employeeName",
						       c.change_type as "changeType", to_char(c.effective_date, 'YYYY-MM-DD') as "effectiveDate", c.applicant, c.reviewer,
						       c.reason, c.error_message as "errorMessage", c.applied_at as "appliedAt",
						       case when c.status='REVIEWING' and w.status in ('CANCELLED','TERMINATED','WITHDRAWN') then 'CANCELLED' else c.status end as status
						from edu_teacher_personnel_case c join wf_instance w on w.id=c.instance_id
						where (? = '' or c.organization_id=?) and (? = '' or c.applicant=?)
						  and (? = '' or (case when c.status='REVIEWING' and w.status in ('CANCELLED','TERMINATED','WITHDRAWN')
						       then 'CANCELLED' else c.status end)=?)
						order by c.created_at desc, c.instance_id limit ? offset ?
						""",
				organization, organization, applicant, applicant, status, status, size, (long) page * size);
	}

	public Case detail(String id) {
		return jdbc
				.query("""
						select instance_id, employee_id, organization_id, target_organization_id, change_type,
						       effective_date, reason, applicant, reviewer, status
						from edu_teacher_personnel_case where instance_id=?
						""",
						(row, index) -> new Case(row.getString(1), row.getString(2), row.getString(3), row.getString(4),
								row.getString(5), row.getDate(6).toLocalDate(), row.getString(7), row.getString(8),
								row.getString(9), row.getString(10)),
						id)
				.stream().findFirst().orElseThrow(() -> new IllegalArgumentException("教师人事申请不存在"));
	}

	public record Case(String id, String employeeId, String organizationId, String targetOrganizationId,
			String changeType, LocalDate effectiveDate, String reason, String applicant, String reviewer,
			String status) {
	}

	private void assertIndependentReviewer(Case item, String username) {
		var user = users.findByUsername(username);
		if (item.applicant().equals(username) || user == null || item.employeeId().equals(user.getEmployeeId())) {
			throw new AccessDeniedException("人事申请须由独立人员审批");
		}
	}

	private List<String> reviewers(String organization, String target, String applicant, String employeeId) {
		var role = roles.findByRoleCode("EDU_TEACHER_PERSONNEL_REVIEWER");
		var candidates = role == null || !Integer.valueOf(1).equals(role.getStatus()) ? List.<String>of()
				: users.findByRoles_Id(role.getId()).stream()
						.filter(user -> Integer.valueOf(1).equals(user.getStatus())
								&& !Boolean.TRUE.equals(user.getAccountLocked()))
						.filter(user -> !applicant.equals(user.getUsername())
								&& !employeeId.equals(user.getEmployeeId()))
						.filter(user -> reviewerCanAccess(user.getUsername(), user.getEmployeeId(), organization,
								target))
						.map(user -> user.getUsername()).sorted().toList();
		if (candidates.isEmpty())
			throw new IllegalStateException("请先为学校配置一名有数据权限的独立教师人事审核员");
		return candidates;
	}

	private boolean reviewerCanAccess(String username, String employeeId, String organization, String target) {
		var scope = dataScopes.resolve(username);
		if (scope.fullAccess())
			return true;
		var allowed = new java.util.HashSet<>(scope.organizationIds());
		if (employeeId != null)
			assignments.findCurrentAssignments(employeeId, LocalDate.now())
					.forEach(item -> allowed.add(item.getOrganizationId()));
		return allowed.contains(organization) && (target == null || allowed.contains(target));
	}

	private String school(String code) {
		var school = organizations.findByOrgCode(code);
		if (school == null || !"SCHOOL".equals(school.getOrganizationType())
				|| !Integer.valueOf(1).equals(school.getStatus())) {
			throw new IllegalArgumentException("学校编码不存在或学校未启用");
		}
		return school.getId();
	}

	private static Authentication authentication(String username) {
		var actor = SecurityContextHolder.getContext().getAuthentication();
		if (actor == null || !actor.isAuthenticated() || !username.equals(actor.getName())) {
			throw new AccessDeniedException("教师人事流程需要登录身份");
		}
		return actor;
	}

	private static String type(String flow) {
		if (flow == null || !flow.startsWith(PREFIX))
			return null;
		String type = flow.substring(PREFIX.length());
		return TYPES.contains(type) ? type : null;
	}

	private static String text(Map<String, Object> form, String key) {
		return form.get(key) == null ? "" : String.valueOf(form.get(key)).strip();
	}

	private static String required(Map<String, Object> form, String key, String label) {
		String value = text(form, key);
		if (value.isEmpty())
			throw new IllegalArgumentException(label + "不能为空");
		return value;
	}
}
