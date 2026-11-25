package com.chronos.delivery;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Organization;
import com.chronos.model.pojo.Employee;
import com.chronos.service.iService.IIamDirectoryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustomerDelegation {
	static final String GROUP_ROLE = "DELIVERY_GROUP_ADMIN";
	static final String ACCESS_MENU = "76207000-0000-4000-8000-000000000002";
	private final JdbcTemplate jdbc;
	private final IAdminUserRepository users;
	private final IOrganizationRepository organizations;
	private final PasswordEncoder encoder;
	private final IIamDirectoryService directory;

	CustomerDelegation(JdbcTemplate jdbc, IAdminUserRepository users, IOrganizationRepository organizations,
			PasswordEncoder encoder, IIamDirectoryService directory) {
		this.jdbc = jdbc;
		this.users = users;
		this.organizations = organizations;
		this.encoder = encoder;
		this.directory = directory;
	}

	record School(String code, String name) {
	}

	record Account(String username, String displayName, String password, String organizationId) {
	}

	record Grant(String username, String module, Boolean manage, Boolean enabled, String duty) {
	}

	void initialize(String groupUsername, String groupName, String password, List<String> modules) {
		String name = username(groupUsername);
		String groupRole = role(GROUP_ROLE, "集团授权管理员");
		insertMenu(groupRole, ACCESS_MENU);
		var user = users.findByUsername(name);
		if (user == null) {
			user = firstAccount(name, groupName, password, null);
		} else if (!Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM t_user_role WHERE user_id=? AND role_id=?)
				""", Boolean.class, user.getId(), groupRole))) {
			throw new IllegalStateException("已存在同名账号，请核对集团管理员初始化配置");
		}
		assign(user.getId(), groupRole);
		for (var product : licensed(modules)) {
			addPermissions(groupRole, product, true);
			if ("EDU_TEACHER_PERSONNEL".equals(product.code())) grantPersonnelStart(GROUP_ROLE);
		}
		if (modules.contains("AI_KNOWLEDGE") || modules.contains("EDU_OPERATIONS_AGENT")) {
			grantRole(groupRole, java.util.Set.of("ai:model:manage"), List.of("/admin/ai-models"));
		}
	}

	List<Map<String, Object>> accounts(String actor, boolean platformOwner) {
		requireGroup(actor, platformOwner);
		return jdbc.queryForList("""
				SELECT d.username,u.display_name,d.organization_id,o.organization_name,
				coalesce((SELECT string_agg(r.role_code,',' ORDER BY r.role_code) FROM t_user_role ur
				 JOIN t_role r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code LIKE 'DELIVERY_SCHOOL_%'),'') AS roles
				FROM cd_delegated_user d JOIN t_admin_user u ON u.username=d.username
				JOIN t_organization o ON o.id=d.organization_id
				ORDER BY o.organization_name,d.username
				""");
	}

	@Transactional
	String createSchool(School command, String actor, boolean platformOwner) {
		requireGroup(actor, platformOwner);
		String code = SalesDeliveryService.required(command.code(), 64, "学校编码");
		String name = SalesDeliveryService.required(command.name(), 160, "学校名称");
		if (!code.matches("[A-Za-z0-9_-]+") || organizations.findByOrgCode(code) != null) {
			throw new IllegalArgumentException("学校编码无效或已经存在");
		}
		var school = new Organization();
		school.setOrgCode(code);
		school.setOrganizationName(name);
		school.setOrganizationType("SCHOOL");
		school.setRegisterTime(LocalDateTime.now());
		school.setCreateTime(LocalDateTime.now());
		school.setCreateBy(actor);
		school = organizations.saveAndFlush(school);
		addScope(groupRoleId(), school.getId());
		return school.getId();
	}

	@Transactional
	void createAccount(Account command, String actor, boolean platformOwner) {
		requireGroup(actor, platformOwner);
		requireSchool(command.organizationId());
		String name = username(command.username());
		if (users.findByUsername(name) != null) throw new IllegalArgumentException("账号已存在");
		firstAccount(name, command.displayName(), command.password(), command.organizationId());
		jdbc.update("INSERT INTO cd_delegated_user(username,organization_id) VALUES (?,?)", name, command.organizationId());
	}

	@Transactional
	void delegate(Grant command, String actor, boolean platformOwner, List<String> modules) {
		requireGroup(actor, platformOwner);
		var product = licensed(modules).stream().filter(item -> item.code().equals(command.module()))
				.findFirst().orElseThrow(() -> new AccessDeniedException("该模块未授权"));
		if ("THIRD_PARTY_INTEGRATIONS".equals(product.code())) {
			throw new AccessDeniedException("集成配置属于部署级权限，不能委派给学校账号");
		}
		if (command.enabled() == null) throw new IllegalArgumentException("请选择授予或撤销");
		String name = SalesDeliveryService.required(command.username(), 100, "用户名");
		List<String> schools = jdbc.queryForList("SELECT organization_id FROM cd_delegated_user WHERE username=? FOR UPDATE",
				String.class, name);
		if (schools.isEmpty()) throw new AccessDeniedException("只能为集团管理的学校账号授权");
		String school = schools.getFirst();
		requireSchool(school);
		var user = users.findByUsername(name);
		if (user == null || !school.equals(user.getOrganizationId())) {
			throw new AccessDeniedException("账号所属学校已变更");
		}
		boolean manage = Boolean.TRUE.equals(command.manage());
		String duty = command.duty();
		if (duty != null && (manage || !validDuty(product.code(), duty))) {
			throw new IllegalArgumentException("请选择该产品支持的业务岗位");
		}
		String roleType = duty != null ? duty : (manage ? "MANAGE" : "VIEW");
		String code = "DELIVERY_SCHOOL_" + product.code() + "_" + roleType + "_" + school;
		// 按学校和模块生成固定角色，委派调用只能选目录中的业务权限。
		String label = switch (roleType) {
			case "SUPERVISOR" -> "督导员";
			case "TEACHER" -> "授课教师";
			case "PERSONNEL_REVIEWER" -> "校级人事审核员";
			case "PERSONNEL_GROUP_REVIEWER" -> "集团人事审核员";
			case "OFFICE_APPROVER" -> "校级办公审批员";
			case "MANAGE" -> "学校管理员";
			default -> "学校查看员";
		};
		String role = role(code, product.name() + label);
		addScope(role, school);
		if ("PERSONNEL_GROUP_REVIEWER".equals(duty)) {
			var allowed = jdbc.queryForList("""
					select o.id from t_organization o
					join t_role_data_scope s on s.organization_id=o.id
					where s.role_id=? and o.status=1 and o.organization_type='SCHOOL'
					""", String.class, groupRoleId());
			allowed.forEach(id -> addScope(role, id));
		}
		if (duty == null) {
			if ("COLLABORATION_OFFICE".equals(product.code())) {
				grantCollaborationRuntime(role, manage, false);
				if (manage && command.enabled()) {
					bindOfficeEmployee(user, school);
					grantOfficeStart(code);
				}
			} else {
				addPermissions(role, product, manage);
			}
			if (manage && "EDU_TEACHER_PERSONNEL".equals(product.code())) grantPersonnelStart(code);
		} else if ("COLLABORATION_OFFICE".equals(product.code())) {
			grantCollaborationRuntime(role, false, true);
		} else if ("EDU_TEACHER_PERSONNEL".equals(product.code())) {
			grantRole(role, java.util.Set.of("education:teacher:business:review"),
					List.of("/admin/education/teacher-personnel"));
		} else {
			grantSupervisionDuty(role, duty);
		}
		if (command.enabled()) {
			assign(user.getId(), role);
		} else {
			jdbc.update("DELETE FROM t_user_role WHERE user_id=? AND role_id=?", user.getId(), role);
		}
	}

	private boolean validDuty(String product, String duty) {
		return switch (product) {
			case "COLLABORATION_OFFICE" -> "OFFICE_APPROVER".equals(duty);
			case "EDU_SUPERVISION" -> java.util.Set.of("SUPERVISOR", "TEACHER").contains(duty);
			case "EDU_TEACHER_PERSONNEL" -> java.util.Set.of("PERSONNEL_REVIEWER", "PERSONNEL_GROUP_REVIEWER").contains(duty);
			default -> false;
		};
	}

	private void grantPersonnelStart(String code) {
		jdbc.update("""
				insert into wf_definition_acl(id,definition_id,subject_type,subject_id,action,enabled,create_by,create_time)
				select md5(d.id || ':' || ?),d.id,'ROLE',?,'START',true,'DELIVERY',now()
				from wf_definition d where d.flow_code in ('EDU_TEACHER_PERSONNEL_ONBOARD',
				 'EDU_TEACHER_PERSONNEL_TERMINATE','EDU_TEACHER_PERSONNEL_TRANSFER',
				 'EDU_TEACHER_PERSONNEL_SUSPEND','EDU_TEACHER_PERSONNEL_RESUME')
				on conflict do nothing
				""", code, code);
	}

	private void grantSupervisionDuty(String role, String duty) {
		if ("SUPERVISOR".equals(duty)) {
			grantRole(role, java.util.Set.of("education:supervision:assignment:view",
					"education:supervision:assignment:accept", "education:supervision:assignment:check-in",
					"education:supervision:assignment:update", "education:supervision:record:create",
					"education:supervision:record:view"), List.of("/portal/education/supervision"));
		} else {
			grantRole(role, java.util.Set.of("education:supervision:rectification:create"),
					List.of("/portal/education/supervision/teaching"));
		}
	}

	private void requireGroup(String actor, boolean platformOwner) {
		if (!platformOwner && !Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM t_admin_user u JOIN t_user_role ur ON ur.user_id=u.id
				JOIN t_role r ON r.id=ur.role_id WHERE u.username=? AND r.role_code=? AND r.status=1)
				""", Boolean.class, actor, GROUP_ROLE))) {
			throw new AccessDeniedException("无权管理集团交付授权");
		}
	}

	private void requireSchool(String id) {
		if (!Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM t_role_data_scope s JOIN t_organization o ON o.id=s.organization_id
				WHERE s.role_id=? AND o.id=? AND o.status=1 AND o.organization_type='SCHOOL')
				""", Boolean.class, groupRoleId(), id))) {
			throw new AccessDeniedException("学校不属于集团管理范围");
		}
	}

	private String groupRoleId() {
		return jdbc.queryForObject("SELECT id FROM t_role WHERE role_code=?", String.class, GROUP_ROLE);
	}

	private void addScope(String role, String school) {
		jdbc.update("""
				INSERT INTO t_role_data_scope(id,create_by,create_time,organization_id,role_id,scope_type)
				SELECT ?,'DELIVERY',now(),?,?,'CUSTOM_ORGANIZATION'
				WHERE NOT EXISTS (SELECT 1 FROM t_role_data_scope WHERE role_id=? AND organization_id=?)
				ON CONFLICT (id) DO NOTHING
				""", UUID.nameUUIDFromBytes((role + ":" + school).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(),
				school, role, role, school);
	}

	private void addPermissions(String role, DeliveryCatalog.Product product, boolean manage) {
		var codes = new java.util.TreeSet<>(product.readPermissions());
		if (manage) codes.addAll(product.writePermissions());
		grantRole(role, codes, product.menus());
	}

	private void grantOfficeStart(String roleCode) {
		jdbc.update("""
				INSERT INTO wf_definition_acl(id,definition_id,subject_type,subject_id,action,enabled,create_by,create_time)
				SELECT md5(d.id || ':' || ?),d.id,'ROLE',?,'START',true,'DELIVERY',now()
				FROM wf_definition d
				WHERE d.flow_code IN ('STAFF_BUSINESS_TRIP_APPROVAL','STAFF_VEHICLE_USE_APPROVAL',
				  'STAFF_SEAL_USE_APPROVAL','OFFICIAL_DOCUMENT_ISSUE_APPROVAL','EDU_TEACHER_LEAVE_APPROVAL')
				ON CONFLICT (definition_id,subject_type,subject_id,action) DO NOTHING
				""", roleCode, roleCode);
	}

	private void bindOfficeEmployee(AdminUser user, String school) {
		if (user.getEmployeeId() != null) return;
		var employee = new Employee();
		employee.setEmployeeName(user.getDisplayName());
		employee.setNumberingOrganizationId(school);
		employee.setHireDate(LocalDate.now());
		user.setEmployeeId(directory.saveEmployee(employee).getId());
		users.save(user);
	}

	private void grantCollaborationRuntime(String role, boolean manage, boolean reviewer) {
		var permissions = new java.util.TreeSet<>(java.util.Set.of("workflow:instance:view"));
		if (manage) permissions.addAll(java.util.Set.of("workflow:instance:start", "workflow:instance:withdraw",
				"collaboration:office:apply"));
		if (reviewer) permissions.addAll(java.util.Set.of("workflow:task:claim", "workflow:task:approve",
				"workflow:task:reject", "collaboration:office:manage"));
		grantRole(role, permissions, List.of("/portal/collaboration", "/portal/collaboration/business-trips",
				"/portal/collaboration/documents", "/portal/education/leaves", "/portal/workflows", "/portal/tasks",
				"/portal/workflow-notifications"));
	}

	private void grantRole(String role, java.util.Set<String> codes, List<String> menus) {
		for (String code : codes) {
			jdbc.update("""
					INSERT INTO t_role_permission(role_id,permission_id)
					SELECT ?,id FROM t_permission WHERE permission_code=? AND status=1 ON CONFLICT DO NOTHING
					""", role, code);
		}
		for (String path : menus) {
			jdbc.update("""
					INSERT INTO t_role_menu(role_id,menu_id) SELECT ?,id FROM t_menu WHERE path=? ON CONFLICT DO NOTHING
					""", role, path);
		}
	}

	private void insertMenu(String role, String menu) {
		jdbc.update("INSERT INTO t_role_menu(role_id,menu_id) VALUES (?,?) ON CONFLICT DO NOTHING", role, menu);
	}

	private void assign(String user, String role) {
		jdbc.update("INSERT INTO t_user_role(user_id,role_id) VALUES (?,?) ON CONFLICT DO NOTHING", user, role);
	}

	private String role(String code, String name) {
		jdbc.update("""
				INSERT INTO t_role(id,create_by,create_time,role_code,role_name,status,built_in)
				VALUES (?,'DELIVERY',now(),?,?,1,true) ON CONFLICT (role_code) DO NOTHING
				""", UUID.randomUUID().toString(), code, name);
		return jdbc.queryForObject("SELECT id FROM t_role WHERE role_code=?", String.class, code);
	}

	private AdminUser firstAccount(String username, String display, String password, String school) {
		if (password == null || password.length() < 12 || password.length() > 64
				|| !password.matches(".*[A-Z].*") || !password.matches(".*[a-z].*")
				|| !password.matches(".*\\d.*") || !password.matches(".*[^A-Za-z0-9].*")) {
			throw new IllegalArgumentException("初始密码须为 12 至 64 位，包含大小写字母、数字和符号");
		}
		var user = new AdminUser();
		user.setUsername(username);
		user.setDisplayName(SalesDeliveryService.required(display, 100, "显示名称"));
		user.setPassword(encoder.encode(password));
		user.setOrganizationId(school);
		user.setStatus(1);
		user.setMustChangePassword(true);
		user.setCreateTime(LocalDateTime.now());
		user.setCreateBy("DELIVERY");
		return users.saveAndFlush(user);
	}

	private String username(String value) {
		String name = SalesDeliveryService.required(value, 100, "用户名");
		if (!name.matches("[A-Za-z0-9._@-]{3,100}") || "admin".equalsIgnoreCase(name)) {
			throw new IllegalArgumentException("用户名格式无效");
		}
		return name;
	}

	private List<DeliveryCatalog.Product> licensed(Collection<String> modules) {
		return DeliveryCatalog.PRODUCTS.stream().filter(product -> modules.contains(product.code())).toList();
	}
}
