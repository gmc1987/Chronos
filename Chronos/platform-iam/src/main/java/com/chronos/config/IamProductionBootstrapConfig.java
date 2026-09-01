package com.chronos.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IPermissionRepository;
import com.chronos.Idao.IRoleMenuPermissionRepository;
import com.chronos.Idao.IRolePermissionRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.model.pojo.Permission;
import com.chronos.model.pojo.RolePermission;

@Configuration
public class IamProductionBootstrapConfig {
    @Bean
    ApplicationRunner iamProductionBootstrap(IAdminUserRepository users, IRoleRepository roles,
            IPermissionRepository permissions, IRolePermissionRepository rolePermissions,
            IRoleMenuPermissionRepository legacyRelations) {
        return args -> migrate(users, roles, permissions, rolePermissions, legacyRelations);
    }

    @Transactional
    void migrate(IAdminUserRepository users, IRoleRepository roles, IPermissionRepository permissions,
            IRolePermissionRepository rolePermissions, IRoleMenuPermissionRepository legacyRelations) {
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
        required.put("iam:organization:manage","医院组织管理"); required.put("iam:directory:manage","员工岗位任职管理");
        required.put("iam:dictionary:manage","字典管理"); required.put("iam:audit:view","IAM审计查询");
        required.put("portal:manage","门户配置管理");
        required.put("workflow:manage","流程中心管理"); required.put("workflow:use","流程发起与审批");
        required.forEach((code,name)->{
            if(permissions.findByPermissionCode(code)==null){Permission p=new Permission();p.setPermissionCode(code);p.setPermissionName(name);p.setPermissionType("API");p.setStatus(1);permissions.save(p);}
        });
        var allIamPermissionIds=permissions.findAll().stream().filter(p->p.getPermissionCode().startsWith("iam:")||"portal:manage".equals(p.getPermissionCode())||p.getPermissionCode().startsWith("workflow:")).map(p->p.getId()).toList();
        roles.findAll().stream().filter(r->Boolean.TRUE.equals(r.getBuiltIn())&&"SUPER_ADMIN".equalsIgnoreCase(r.getRoleCode())).forEach(role->{
            Set<String> existing=rolePermissions.findByRoleId(role.getId()).stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());
            rolePermissions.saveAll(allIamPermissionIds.stream().filter(id->!existing.contains(id)).map(id->new RolePermission(role.getId(),id)).toList());
        });
    }
    private boolean isAdmin(String value){if(value==null)return false;String v=value.toLowerCase();return v.contains("admin")||v.contains("管理员");}
    private String normalize(String name,String id){String value=name==null?"ROLE":name.trim().toUpperCase().replaceAll("[^A-Z0-9\\u4e00-\\u9fa5]+","_");return value+"_"+id.substring(0,Math.min(8,id.length())).toUpperCase();}
}
