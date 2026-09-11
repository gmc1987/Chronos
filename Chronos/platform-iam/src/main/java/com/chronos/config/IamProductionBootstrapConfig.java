package com.chronos.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IPermissionRepository;
import com.chronos.Idao.IRoleMenuPermissionRepository;
import com.chronos.Idao.IRolePermissionRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.IPortalApplicationRepository;
import com.chronos.Idao.IMenuRepository;
import com.chronos.model.pojo.Permission;
import com.chronos.model.pojo.RolePermission;
import com.chronos.model.pojo.RoleMenuPermission;
import com.chronos.model.pojo.Menu;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.Role;

@Configuration
public class IamProductionBootstrapConfig {
    @Bean
    ApplicationRunner iamProductionBootstrap(IAdminUserRepository users, IRoleRepository roles,
            IPermissionRepository permissions, IRolePermissionRepository rolePermissions,
            IRoleMenuPermissionRepository legacyRelations,IPortalApplicationRepository portalApplications,
            IMenuRepository menus, PlatformTransactionManager transactionManager,
            PasswordEncoder passwordEncoder,
            @Value("${CHRONOS_BOOTSTRAP_ADMIN_USERNAME:}") String bootstrapUsername,
            @Value("${CHRONOS_BOOTSTRAP_ADMIN_PASSWORD:}") String bootstrapPassword,
            @Value("${CHRONOS_BOOTSTRAP_ADMIN_DISPLAY_NAME:系统管理员}") String bootstrapDisplayName) {
        return args -> {
            // ApplicationRunner 返回的 lambda 不经过当前配置类的事务代理。
            // 显式包裹事务，确保角色的 menus 懒加载集合在整个迁移过程中始终绑定 Session。
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
            transactionTemplate.executeWithoutResult(status -> {
                bootstrapFirstAdministrator(
                        users,
                        roles,
                        passwordEncoder,
                        bootstrapUsername,
                        bootstrapPassword,
                        bootstrapDisplayName);
                migrate(
                        users,
                        roles,
                        permissions,
                        rolePermissions,
                        legacyRelations,
                        portalApplications,
                        menus);
            });
        };
    }

    /**
     * 只在完全空的用户表中创建首个管理员。凭据必须由部署环境提供，
     * 禁止在代码、SQL 或默认配置中保留可登录的固定密码。
     */
    void bootstrapFirstAdministrator(
            IAdminUserRepository users,
            IRoleRepository roles,
            PasswordEncoder passwordEncoder,
            String username,
            String password,
            String displayName) {
        if (users.count() > 0) {
            return;
        }

        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "IAM 用户表为空，请通过 CHRONOS_BOOTSTRAP_ADMIN_USERNAME 和 "
                            + "CHRONOS_BOOTSTRAP_ADMIN_PASSWORD 配置首个管理员");
        }
        if (!normalizedUsername.matches("[A-Za-z0-9._@-]{3,100}")) {
            throw new IllegalStateException("首个管理员用户名格式不合法");
        }
        validateBootstrapPassword(password);

        LocalDateTime now = LocalDateTime.now();
        Role superAdmin = roles.findByRoleCode("SUPER_ADMIN");
        if (superAdmin == null) {
            superAdmin = new Role();
            superAdmin.setRoleCode("SUPER_ADMIN");
            superAdmin.setRoleName("系统管理员");
            superAdmin.setBuiltIn(true);
            superAdmin.setStatus(1);
            superAdmin.setDescription("平台内置超级管理员角色");
            superAdmin.setCreateBy("bootstrap");
            superAdmin.setCreateTime(now);
            superAdmin = roles.saveAndFlush(superAdmin);
        }

        AdminUser administrator = new AdminUser();
        administrator.setUsername(normalizedUsername);
        administrator.setPassword(passwordEncoder.encode(password));
        administrator.setDisplayName(
                displayName == null || displayName.isBlank()
                        ? "系统管理员"
                        : displayName.trim());
        administrator.setAccountType("STAFF");
        administrator.setAccountLocked(false);
        administrator.setFailedLoginAttempts(0);
        administrator.setMustChangePassword(true);
        administrator.setTokenVersion(0);
        administrator.setStatus(1);
        administrator.setCreateBy("bootstrap");
        administrator.setCreateTime(now);
        administrator.getRoles().add(superAdmin);
        users.saveAndFlush(administrator);
    }

    private void validateBootstrapPassword(String password) {
        boolean valid = password.length() >= 12
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*\\d.*")
                && password.matches(".*[^A-Za-z0-9].*");
        if (!valid) {
            throw new IllegalStateException(
                    "首个管理员密码至少 12 位，并包含大小写字母、数字和特殊字符");
        }
    }

    void migrate(IAdminUserRepository users, IRoleRepository roles, IPermissionRepository permissions,
            IRolePermissionRepository rolePermissions, IRoleMenuPermissionRepository legacyRelations) {
        migrate(users,roles,permissions,rolePermissions,legacyRelations,null,null);
    }

    void migrate(IAdminUserRepository users, IRoleRepository roles, IPermissionRepository permissions,
            IRolePermissionRepository rolePermissions, IRoleMenuPermissionRepository legacyRelations,
            IPortalApplicationRepository portalApplications) {
        migrate(users,roles,permissions,rolePermissions,legacyRelations,portalApplications,null);
    }

    void migrate(IAdminUserRepository users, IRoleRepository roles, IPermissionRepository permissions,
            IRolePermissionRepository rolePermissions, IRoleMenuPermissionRepository legacyRelations,
            IPortalApplicationRepository portalApplications, IMenuRepository menus) {
        var allUsers = users.findAll();
        allUsers.forEach(user -> {
            if (user.getAccountType() == null) user.setAccountType("STAFF");
            if (user.getAccountLocked() == null) user.setAccountLocked(false);
            if (user.getFailedLoginAttempts() == null) user.setFailedLoginAttempts(0);
            if (user.getMustChangePassword() == null) user.setMustChangePassword(false);
            if (user.getTokenVersion() == null) user.setTokenVersion(0);
        });
        users.saveAllAndFlush(allUsers);
        var allRoles = roles.findAll();
        allRoles.forEach(role -> {
            if (role.getRoleCode() == null || role.getRoleCode().isBlank()) role.setRoleCode(normalize(role.getRoleName(), role.getId()));
            if (role.getStatus() == null) role.setStatus(1);
            if (role.getBuiltIn() == null) role.setBuiltIn(isAdmin(role.getRoleName()));
            if (rolePermissions.findByRoleId(role.getId()).isEmpty()) {
                Set<String> ids = legacyRelations.findByRoleId(role.getId()).stream().map(x -> x.getPermissionId()).collect(Collectors.toSet());
                rolePermissions.saveAll(ids.stream().map(id -> new RolePermission(role.getId(), id)).toList());
            }
        });
        roles.saveAllAndFlush(allRoles);
        var existingSuperAdmin = allRoles.stream()
                .filter(r -> "SUPER_ADMIN".equalsIgnoreCase(r.getRoleCode())).findFirst();
        if (existingSuperAdmin.isPresent()) {
            existingSuperAdmin.get().setBuiltIn(true);
        } else {
            allRoles.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getBuiltIn()) && isAdmin(r.getRoleName()))
                    .findFirst()
                    .ifPresent(role -> role.setRoleCode("SUPER_ADMIN"));
        }
        roles.saveAllAndFlush(allRoles);
        Map<String,String> required = new LinkedHashMap<>();
        required.put("iam:user:manage","用户账号管理"); required.put("iam:role:manage","角色权限管理");
        required.put("iam:permission:manage","权限定义管理"); required.put("iam:menu:manage","菜单管理");
        required.put("iam:organization:manage","组织机构管理"); required.put("iam:directory:manage","人员岗位任职管理");
		required.put("iam:dictionary:manage", "字典管理");
		required.put("iam:audit:view", "IAM审计查询");
		required.put("iam:audit:export", "导出审计日志");
        required.put("portal:manage","门户配置管理");
        required.put("workflow:manage","流程中心管理"); required.put("workflow:use","流程发起与审批");
        required.put("workflow:definition:view","查看流程定义"); required.put("workflow:definition:create","创建流程定义");
        required.put("workflow:definition:update","编辑流程定义"); required.put("workflow:definition:delete","删除流程定义");
        required.put("workflow:definition:publish","发布与停用流程"); required.put("workflow:form:manage","流程表单管理");
        required.put("workflow:instance:start","发起流程实例"); required.put("workflow:instance:view","查看流程实例");
        required.put("workflow:instance:manage","管理流程实例"); required.put("workflow:instance:terminate","终止流程实例");
        required.put("workflow:monitor:view","查看流程监控"); required.put("workflow:task:approve","审批流程任务");
		required.put("workflow:incident:view", "查看流程事故");
		required.put("workflow:incident:manage", "处置流程事故");
        required.put("workflow:task:reject","拒绝流程任务");
        required.put("workflow:task:return","退回流程任务"); required.put("workflow:task:transfer","转办流程任务");
        required.put("workflow:task:add-sign","加签流程任务"); required.put("workflow:task:cc","抄送流程任务");
        required.put("workflow:task:remind","催办流程任务"); required.put("workflow:instance:withdraw","撤回流程实例");
        required.put("workflow:directory:view","查看流程人员目录");
        required.put("workflow:task:claim", "认领流程候选任务");
        required.put("workflow:delegation:manage", "管理个人流程委托");
		required.put("message:publication:manage", "通知公告管理");
		required.put("message:publication:view", "查看通知公告管理数据");
		required.put("message:publication:create", "新增通知公告");
		required.put("message:publication:update", "编辑通知公告");
		required.put("message:publication:delete", "删除通知公告");
		required.put("message:publication:publish", "发布通知公告");
		required.put("message:publication:withdraw", "撤下通知公告");
		required.put("message:publication:read", "阅读通知公告");
		required.put("message:publication:review", "审核通知公告");
		required.put("message:publication:statistics", "查看通知公告阅读统计");
		required.put("ai:model:manage", "AI模型管理");
        Map<String,String> dataScopes = new LinkedHashMap<>();
        dataScopes.put("ALL","全部数据"); dataScopes.put("ORGANIZATION","本机构"); dataScopes.put("DEPARTMENT","本部门");
        dataScopes.put("DEPARTMENT_AND_CHILDREN","本部门及下级"); dataScopes.put("SELF","仅本人");
        dataScopes.put("CUSTOM_ORGANIZATION","自定义机构"); dataScopes.put("CUSTOM_DEPARTMENT","自定义部门"); dataScopes.put("CUSTOM_EMPLOYEE","自定义员工");
		dataScopes.put("EDUCATION_CLASS", "本班级");
		dataScopes.put("EDUCATION_GRADE", "本年级");
		dataScopes.put("EDUCATION_SUBJECT_GROUP", "本教研组");
		dataScopes.put("CUSTOM_EDUCATION_CLASS", "指定班级");
		dataScopes.put("CUSTOM_EDUCATION_GRADE", "指定年级");
        dataScopes.forEach((scope,name)->required.put("data:scope:"+scope.toLowerCase(),name));
        addAtomic(required,"iam:user","用户",Map.of("view","查看","create","新增","update","修改","disable","停用","unlock","解锁","force-logout","强制下线","reset-password","重置密码"));
        addAtomic(required,"iam:role","角色",Map.of("view","查看","create","新增","update","修改","delete","删除","authorize","授权"));
        addAtomic(required,"iam:menu","菜单",Map.of("view","查看","create","新增","update","修改","delete","删除"));
        addAtomic(required,"iam:organization","机构",Map.of("view","查看","create","新增","update","修改","delete","删除"));
        addAtomic(required,"iam:directory","组织员工",Map.of("view","查看","create","新增","update","修改","delete","删除","import","导入","template","下载模板"));
        addAtomic(required,"iam:dictionary","字典",Map.of("view","查看","create","新增","update","修改","delete","删除"));
        addAtomic(required,"iam:permission","权限定义",Map.of("view","查看","create","新增","update","修改","delete","删除","disable","停用"));
        addAtomic(required,"portal:admin","门户配置",Map.of("view","查看","create","新增","update","修改","delete","删除"));
		addAtomic(required, "ai:model", "AI模型", Map.of(
				"view", "查看",
				"create", "新增",
				"update", "编辑",
				"delete", "删除"));
        required.forEach((code,name)->{
            Permission permission = permissions.findByPermissionCode(code);
            if(permission==null){permission=new Permission();permission.setPermissionCode(code);permission.setPermissionName(name);permission.setPermissionType(permissionType(code));permission.setStatus(1);}
            permission.setBuiltIn(true);
            permissions.save(permission);
        });
        if (menus != null && menus.findAll().stream().noneMatch(menu -> "/admin/permission-definitions".equals(menu.getPath()))) {
            Menu system = menus.findAll().stream().filter(menu -> "/system".equals(menu.getPath())).findFirst().orElse(null);
            Menu definitionMenu = new Menu(); definitionMenu.setMenuName("权限定义"); definitionMenu.setPath("/admin/permission-definitions");
            definitionMenu.setParentId(system == null ? null : system.getId()); definitionMenu.setOrderNum(35); definitionMenu.setCreateTime(LocalDateTime.now());
            menus.saveAndFlush(definitionMenu);
            roles.findAll().stream().filter(role -> "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode())).forEach(role -> {role.getMenus().add(definitionMenu);roles.save(role);});
        }
		if (menus != null
				&& menus.findAll().stream()
						.noneMatch(menu -> "/admin/audit-logs".equals(menu.getPath()))) {
			Menu system = menus.findAll().stream()
					.filter(menu -> "/system".equals(menu.getPath()))
					.findFirst()
					.orElse(null);
			Menu auditMenu = new Menu();
			auditMenu.setMenuName("审计中心");
			auditMenu.setPath("/admin/audit-logs");
			auditMenu.setParentId(system == null ? null : system.getId());
			auditMenu.setOrderNum(36);
			auditMenu.setCreateTime(LocalDateTime.now());
			menus.saveAndFlush(auditMenu);
			roles.findAll().stream()
					.filter(role -> "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode()))
					.forEach(role -> {
						role.getMenus().add(auditMenu);
						roles.save(role);
					});
		}
		if (menus != null
				&& menus.findAll().stream()
						.noneMatch(menu -> "/admin/publications".equals(menu.getPath()))) {
			Menu publicationMenu = new Menu();
			publicationMenu.setMenuName("通知公告");
			publicationMenu.setPath("/admin/publications");
			publicationMenu.setOrderNum(70);
			publicationMenu.setCreateTime(LocalDateTime.now());
			menus.saveAndFlush(publicationMenu);
			roles.findAll().stream()
					.filter(role -> "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode()))
					.forEach(role -> {
						role.getMenus().add(publicationMenu);
						roles.save(role);
					});
		}
		if (menus != null) {
			Menu aiModelMenu = menus.findAll().stream()
					.filter(menu -> "/admin/ai-models".equals(menu.getPath())
							|| "/admin/ai-model/models".equals(menu.getPath()))
					.findFirst()
					.orElse(null);
			if (aiModelMenu == null) {
				aiModelMenu = new Menu();
				aiModelMenu.setCreateTime(LocalDateTime.now());
			}
			aiModelMenu.setMenuName("AI模型管理");
			aiModelMenu.setPath("/admin/ai-models");
			aiModelMenu.setOrderNum(80);
			menus.saveAndFlush(aiModelMenu);
			Menu finalAiModelMenu = aiModelMenu;
			roles.findAll().stream()
					.filter(role -> "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode()))
					.forEach(role -> {
						role.getMenus().add(finalAiModelMenu);
						roles.save(role);
					});
		}
        var classifiedPermissions = permissions.findAll();
        classifiedPermissions.forEach(permission -> {
            permission.setPermissionType(permissionType(permission));
            if (permission.getActionType() == null) permission.setActionType(actionType(permission.getPermissionCode()));
            if (permission.getResourceType() == null) permission.setResourceType(resourceType(permission.getPermissionCode()));
            if (permission.getScopeType() == null && permission.getPermissionCode()!=null && permission.getPermissionCode().startsWith("data:scope:"))
                permission.setScopeType(permission.getPermissionCode().substring("data:scope:".length()).toUpperCase());
            if (isLegacyPermission(permission.getPermissionCode())) permission.setStatus(0);
        });
        if (menus != null) {
            Map<String,String> menuIdsByPath = menus.findAll().stream()
                    .filter(menu -> menu.getPath() != null && !menu.getPath().isBlank())
                    .collect(Collectors.toMap(menu -> menu.getPath(), menu -> menu.getId(), (a,b) -> a));
            classifiedPermissions.forEach(permission -> {
                String mappedMenuId = menuIdsByPath.get(menuPath(permission.getPermissionCode()));
                if (mappedMenuId != null) {
                    permission.setMenuId(mappedMenuId);
                } else if (!"MENU_ACTION".equals(permission.getPermissionType())) {
                    permission.setMenuId(null);
                }
                // 行业模板和用户自定义的菜单操作权限不在平台内置路径映射中。
                // 此时必须保留原 menuId，避免应用重启后清空所属菜单。
            });
        }
        permissions.saveAllAndFlush(classifiedPermissions);
        Map<String,Permission> permissionsById = classifiedPermissions.stream()
                .collect(Collectors.toMap(Permission::getId, permission -> permission));
        Set<String> workflowPermissionIds = classifiedPermissions.stream()
                .filter(permission -> "WORKFLOW".equals(permission.getPermissionType()))
                .map(Permission::getId).collect(Collectors.toSet());
        var currentMenuRelations = legacyRelations.findAll();
        legacyRelations.deleteAll(currentMenuRelations.stream().filter(relation -> {
            Permission permission = permissionsById.get(relation.getPermissionId());
            return permission == null || "WORKFLOW".equals(permission.getPermissionType())
                    || permission.getMenuId() == null || !permission.getMenuId().equals(relation.getMenuId());
        }).toList());
        Set<String> normalizedRelations = currentMenuRelations.stream().map(relation -> relation.getRoleId()+":"+relation.getMenuId()+":"+relation.getPermissionId()).collect(Collectors.toSet());
        var menuBoundRolePermissions = rolePermissions.findAll().stream().filter(relation -> {
            Permission permission = permissionsById.get(relation.getPermissionId());
            return permission != null && "MENU_ACTION".equals(permission.getPermissionType()) && permission.getMenuId() != null;
        }).toList();
        legacyRelations.saveAll(menuBoundRolePermissions.stream().map(relation -> {
            Permission permission = permissionsById.get(relation.getPermissionId());
            return new RoleMenuPermission(relation.getRoleId(), permission.getMenuId(), relation.getPermissionId());
        }).filter(relation -> !normalizedRelations.contains(relation.getRoleId()+":"+relation.getMenuId()+":"+relation.getPermissionId())).toList());
        rolePermissions.deleteAll(menuBoundRolePermissions);

        // 超级管理员必须能够完成首次部署后的平台初始化。参考菜单和行业权限由
        // Flyway 在 ApplicationRunner 之前写入，因此这里统一授予全部有效定义，
        // 避免空库管理员虽然能够登录，却看不到教育菜单或无法执行原子操作。
        grantSuperAdministratorAccess(
                roles,
                menus,
                classifiedPermissions,
                rolePermissions,
                legacyRelations);

		var allIamPermissionIds = permissions.findAll().stream()
				.filter(permission -> "WORKFLOW".equals(permission.getPermissionType())
						|| permission.getMenuId() == null
						|| permission.getPermissionCode().startsWith("message:"))
				.filter(permission -> permission.getPermissionCode().startsWith("iam:")
						|| "portal:manage".equals(permission.getPermissionCode())
						|| permission.getPermissionCode().startsWith("workflow:")
						|| permission.getPermissionCode().startsWith("message:")
						|| permission.getPermissionCode().startsWith("ai:model:"))
                .map(p->p.getId()).toList();
        roles.findAll().stream().filter(r->Boolean.TRUE.equals(r.getBuiltIn())&&"SUPER_ADMIN".equalsIgnoreCase(r.getRoleCode())).forEach(role->{
            Set<String> existing=rolePermissions.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());
            rolePermissions.saveAll(allIamPermissionIds.stream().filter(id->!existing.contains(id)).map(id->new RolePermission(role.getId(),id)).toList());
        });
        Permission workflowUse=permissions.findByPermissionCode("workflow:use");
        roles.findAll().stream().filter(r->"ROLE_PLATFORM_USER".equalsIgnoreCase(r.getRoleCode())).forEach(role->{
            boolean exists=rolePermissions.findByRoleId(role.getId()).stream().anyMatch(x->x.getPermissionId().equals(workflowUse.getId()));
            if(!exists)rolePermissions.save(new RolePermission(role.getId(),workflowUse.getId()));
        });
		Permission publicationRead = permissions.findByPermissionCode("message:publication:read");
		roles.findAll().stream()
				.filter(role -> "ROLE_PLATFORM_USER".equalsIgnoreCase(role.getRoleCode()))
				.forEach(role -> {
					boolean exists = rolePermissions.findByRoleId(role.getId()).stream()
							.anyMatch(item -> item.getPermissionId().equals(publicationRead.getId()));
					if (!exists) {
						rolePermissions.save(new RolePermission(role.getId(), publicationRead.getId()));
					}
				});
        Set<String> manageBundle = Set.of(
                "workflow:definition:view",
                "workflow:definition:create",
                "workflow:definition:update",
                "workflow:definition:delete",
                "workflow:definition:publish",
                "workflow:form:manage",
                "workflow:instance:view",
                "workflow:instance:manage",
                "workflow:instance:terminate",
                "workflow:monitor:view",
                "workflow:incident:view",
                "workflow:incident:manage",
                "workflow:directory:view"
        );
        Set<String> useBundle=Set.of("workflow:definition:view","workflow:instance:start","workflow:instance:view","workflow:task:approve","workflow:task:reject","workflow:task:return","workflow:task:transfer","workflow:task:add-sign","workflow:task:cc","workflow:task:remind","workflow:task:claim","workflow:delegation:manage","workflow:instance:withdraw","workflow:directory:view");
        Permission legacyManage=permissions.findByPermissionCode("workflow:manage");
        Map<String,String> permissionIds=permissions.findAll().stream().collect(Collectors.toMap(Permission::getPermissionCode,Permission::getId,(a,b)->a));
        roles.findAll().forEach(role->{Set<String> existing=rolePermissions.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());boolean hasManage=legacyManage!=null&&existing.contains(legacyManage.getId()),hasUse=workflowUse!=null&&existing.contains(workflowUse.getId());Set<String> bundle=new java.util.HashSet<>();if(hasManage)bundle.addAll(manageBundle);if(hasUse)bundle.addAll(useBundle);rolePermissions.saveAll(bundle.stream().map(permissionIds::get).filter(java.util.Objects::nonNull).filter(id->!existing.contains(id)).map(id->new RolePermission(role.getId(),id)).toList());});
        Map<String,Set<String>> legacyBundles=Map.of(
            "iam:user:manage",Set.of("iam:user:view","iam:user:create","iam:user:update","iam:user:disable","iam:user:unlock","iam:user:force-logout","iam:user:reset-password"),
            "iam:role:manage",Set.of("iam:role:view","iam:role:create","iam:role:update","iam:role:delete","iam:role:authorize"),
            "iam:menu:manage",Set.of("iam:menu:view","iam:menu:create","iam:menu:update","iam:menu:delete"),
            "iam:organization:manage",Set.of("iam:organization:view","iam:organization:create","iam:organization:update","iam:organization:delete"),
            "iam:directory:manage",Set.of("iam:directory:view","iam:directory:create","iam:directory:update","iam:directory:delete","iam:directory:import","iam:directory:template"),
            "iam:dictionary:manage",Set.of("iam:dictionary:view","iam:dictionary:create","iam:dictionary:update","iam:dictionary:delete"),
            "iam:permission:manage",Set.of("iam:permission:view","iam:permission:create","iam:permission:update","iam:permission:delete","iam:permission:disable"),
            "portal:manage",Set.of("portal:admin:view","portal:admin:create","portal:admin:update","portal:admin:delete"),
            "ai:model:manage",Set.of("ai:model:view","ai:model:create","ai:model:update","ai:model:delete")
        );
        roles.findAll().forEach(role->{Set<String> existing=rolePermissions.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());existing.addAll(legacyRelations.findByRoleId(role.getId()).stream().map(RoleMenuPermission::getPermissionId).collect(Collectors.toSet()));Set<String> grant=new java.util.HashSet<>();legacyBundles.forEach((legacy,bundle)->{String legacyId=permissionIds.get(legacy);if(legacyId!=null&&existing.contains(legacyId))grant.addAll(bundle);});rolePermissions.saveAll(grant.stream().map(permissionIds::get).filter(java.util.Objects::nonNull).filter(id->!existing.contains(id)).map(id->new RolePermission(role.getId(),id)).toList());});
        if (menus != null) {
            Permission definitionPermission = permissions.findByPermissionCode("iam:permission:manage");
            Menu definitionMenu = menus.findAll().stream().filter(menu -> "/admin/permission-definitions".equals(menu.getPath())).findFirst().orElse(null);
            if (definitionPermission != null && definitionMenu != null) {
                Set<String> authorizedRoleIds = legacyRelations.findAll().stream().filter(relation -> definitionPermission.getId().equals(relation.getPermissionId())).map(RoleMenuPermission::getRoleId).collect(Collectors.toSet());
                roles.findAll().stream().filter(role -> authorizedRoleIds.contains(role.getId()) || "SUPER_ADMIN".equalsIgnoreCase(role.getRoleCode())).forEach(role -> {role.getMenus().add(definitionMenu);roles.save(role);});
            }
        }
        if(portalApplications!=null)portalApplications.findAll().stream().filter(a->"workflow".equalsIgnoreCase(a.getAppCode())).forEach(a->{a.setRequiredPermission("workflow:instance:view");portalApplications.save(a);});
    }

    private void grantSuperAdministratorAccess(
            IRoleRepository roles,
            IMenuRepository menus,
            Iterable<Permission> permissions,
            IRolePermissionRepository rolePermissions,
            IRoleMenuPermissionRepository menuPermissions) {
        if (menus == null) {
            return;
        }

        Role superAdministrator = roles.findByRoleCode("SUPER_ADMIN");
        if (superAdministrator == null) {
            return;
        }

        superAdministrator.getMenus().addAll(menus.findAll());
        roles.saveAndFlush(superAdministrator);

        Set<String> existingGlobalPermissionIds = rolePermissions
                .findByRoleId(superAdministrator.getId())
                .stream()
                .map(RolePermission::getPermissionId)
                .collect(Collectors.toSet());
        Set<String> existingMenuPermissionKeys = menuPermissions
                .findByRoleId(superAdministrator.getId())
                .stream()
                .map(relation -> relation.getMenuId() + ":" + relation.getPermissionId())
                .collect(Collectors.toSet());

        for (Permission permission : permissions) {
            if (!Integer.valueOf(1).equals(permission.getStatus())) {
                continue;
            }

            if ("MENU_ACTION".equals(permission.getPermissionType())
                    && permission.getMenuId() != null
                    && !permission.getMenuId().isBlank()) {
                String relationKey = permission.getMenuId() + ":" + permission.getId();
                if (existingMenuPermissionKeys.add(relationKey)) {
                    menuPermissions.save(new RoleMenuPermission(
                            superAdministrator.getId(),
                            permission.getMenuId(),
                            permission.getId()));
                }
                continue;
            }

            if (existingGlobalPermissionIds.add(permission.getId())) {
                rolePermissions.save(new RolePermission(
                        superAdministrator.getId(),
                        permission.getId()));
            }
        }
    }

    private boolean isAdmin(String value){if(value==null)return false;String v=value.toLowerCase();return v.contains("admin")||v.contains("管理员");}
    private String permissionType(String code){return code!=null&&code.startsWith("workflow:")?"WORKFLOW":code!=null&&code.startsWith("data:scope:")?"DATA":"MENU_ACTION";}
    private String permissionType(Permission permission){String current=permission.getPermissionType();return current!=null&&Set.of("MENU_ACTION","WORKFLOW","DATA").contains(current.toUpperCase())?current.toUpperCase():permissionType(permission.getPermissionCode());}
    private String actionType(String code){if(code==null)return null;int i=code.lastIndexOf(':');return i<0?"MANAGE":switch(code.substring(i+1)){case "list","view"->"VIEW";case "add","create"->"CREATE";case "update"->"UPDATE";case "delete"->"DELETE";case "upload"->"UPLOAD";case "download"->"DOWNLOAD";default->"MANAGE";};}
    private String resourceType(String code){if(code==null||!code.startsWith("workflow:"))return null;String[] parts=code.split(":");return parts.length>1?parts[1].toUpperCase():"WORKFLOW";}
    private void addAtomic(Map<String,String> target,String prefix,String resource,Map<String,String> actions){actions.forEach((action,name)->target.put(prefix+":"+action,name+resource));}
    private boolean isLegacyPermission(String code){return code!=null&&(Set.of("iam:user:manage","iam:role:manage","iam:menu:manage","iam:organization:manage","iam:directory:manage","iam:dictionary:manage","iam:permission:manage","portal:manage","workflow:manage","workflow:use").contains(code)||code.startsWith("system:permissions:"));}
    private String menuPath(String code){
        if(code==null)return null;
        if(code.startsWith("system:permissions:"))return "/admin/permission-definitions";
        if(code.startsWith("iam:user:"))return "/system/users";
        if(code.startsWith("iam:role:"))return "/system/roles";
        if(code.startsWith("iam:permission:"))return "/admin/permission-definitions";
        if(code.startsWith("iam:menu:"))return "/system/menus";
        if(code.startsWith("iam:organization:"))return "/admin/organizations";
        if(code.startsWith("iam:directory:"))return "/admin/directory";
        if(code.startsWith("iam:dictionary:"))return "/system/dicts";
		if (code.startsWith("iam:audit:")) return "/admin/audit-logs";
        if(code.startsWith("portal:admin:"))return "/admin/portal";
		if (code.startsWith("message:publication:")) return "/admin/publications";
		if (code.startsWith("ai:model:")) return "/admin/ai-models";
        return switch(code){
            case "iam:user:manage" -> "/system/users";
            case "iam:role:manage" -> "/system/roles";
            case "iam:permission:manage" -> "/admin/permission-definitions";
            case "iam:menu:manage" -> "/system/menus";
            case "iam:organization:manage" -> "/admin/organizations";
            case "iam:directory:manage" -> "/admin/directory";
            case "iam:dictionary:manage" -> "/system/dicts";
            case "portal:manage" -> "/admin/portal";
            default -> null;
        };
    }
    private String normalize(String name,String id){String value=name==null?"ROLE":name.trim().toUpperCase().replaceAll("[^A-Z0-9\\u4e00-\\u9fa5]+","_");return value+"_"+id.substring(0,Math.min(8,id.length())).toUpperCase();}
}
