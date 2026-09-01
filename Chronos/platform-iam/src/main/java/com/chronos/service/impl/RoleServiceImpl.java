package com.chronos.service.impl;

import com.chronos.Idao.IMenuRepository;
import com.chronos.Idao.IRoleMenuPermissionRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.Idao.IRolePermissionRepository;
import com.chronos.Idao.IRoleDataScopeRepository;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.commons.utils.BeanCopyUtil;
import com.chronos.model.dto.RoleDTO;
import com.chronos.model.dto.RoleMenuPermissionDTO;
import com.chronos.model.pojo.Menu;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.RoleMenuPermission;
import com.chronos.model.pojo.RolePermission;
import com.chronos.model.pojo.RoleDataScope;
import com.chronos.model.dto.RoleDataScopeDTO;
import com.chronos.model.vo.RoleDetailVO;
import com.chronos.model.vo.RoleVO;
import com.chronos.service.iService.IRoleService;
import com.chronos.service.iService.IRefreshTokenService;
import com.chronos.service.iService.IAuditLogService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("roleService")
public class RoleServiceImpl implements IRoleService {
	@Autowired
	private IRoleRepository roleRepository;
	@Autowired
	private IMenuRepository menuRepository;
	@Autowired
	private IRoleMenuPermissionRepository roleMenuPermissionRepository;
	@Autowired private IRolePermissionRepository rolePermissionRepository;
	@Autowired private IRoleDataScopeRepository roleDataScopeRepository;
	@Autowired private IAdminUserRepository adminUserRepository;
	@Autowired private IRefreshTokenService refreshTokenService;
	@Autowired private IAuditLogService auditLogService;

	public Page<Role> pageRoles(RoleDTO dto, Pageable pageable) {
		return this.roleRepository.findAll(pageable);
	}

	public RoleVO getRoleById(String id) {
		Optional<Role> opt = this.roleRepository.findById(id);
		if (!opt.isPresent())
			return null;
		Role r = opt.get();
		RoleVO vo = RoleVO.builder().id(r.getId()).roleName(r.getRoleName()).roleCode(r.getRoleCode())
				.status(r.getStatus()).builtIn(r.getBuiltIn()).description(r.getDescription()).build();
		return vo;
	}

	public RoleDetailVO getRoleDetail(String id) {
		Optional<Role> opt = roleRepository.findById(id);
		if (opt.isEmpty()) {
			return null;
		}

		Role r = opt.get();

		// 1. menuIds
		Set<String> menuIds = new HashSet<>();
		if (r.getMenus() != null) {
			for (Menu m : r.getMenus()) {
				menuIds.add(m.getId());
			}
		}

		// 2. 查询关系表
		List<RoleMenuPermission> relations = roleMenuPermissionRepository.findByRoleId(r.getId());

		// 3. permissionIds
		Set<String> permissionIds = rolePermissionRepository.findByRoleId(r.getId()).stream()
				.map(RolePermission::getPermissionId).collect(Collectors.toSet());
		if (permissionIds.isEmpty()) permissionIds = relations.stream().map(RoleMenuPermission::getPermissionId).collect(Collectors.toSet());

		// 4. menu -> permissionIds 映射
		Map<String, Set<String>> menuPermissionMap = relations.stream()
				.collect(Collectors.groupingBy(RoleMenuPermission::getMenuId,
						Collectors.mapping(RoleMenuPermission::getPermissionId, Collectors.toSet())));

		// 5. 转 DTO
		List<RoleMenuPermissionDTO> menuPermissions = menuPermissionMap.entrySet().stream()
				.map(entry -> new RoleMenuPermissionDTO(entry.getKey(), entry.getValue())).collect(Collectors.toList());

		// 6. 返回
		List<RoleDataScopeDTO> scopes = roleDataScopeRepository.findByRoleIdIn(List.of(r.getId())).stream()
				.map(s -> RoleDataScopeDTO.builder().scopeType(s.getScopeType()).organizationId(s.getOrganizationId())
						.organizationUnitId(s.getOrganizationUnitId()).employeeId(s.getEmployeeId()).build()).toList();
		return RoleDetailVO.builder().id(r.getId()).roleName(r.getRoleName()).roleCode(r.getRoleCode())
				.status(r.getStatus()).builtIn(r.getBuiltIn()).description(r.getDescription())
				.menuIds(menuIds).permissionIds(permissionIds).menuPermissions(menuPermissions).dataScopes(scopes).build();
	}

	@Transactional
	public void save(RoleDTO dto) {
		validateRole(dto, null);
		if ("SUPER_ADMIN".equalsIgnoreCase(dto.getRoleCode())) throw new IllegalArgumentException("SUPER_ADMIN is a reserved role code");
		dto.setBuiltIn(false);
		Role r = new Role();
		BeanCopyUtil.copyNonNullProperties(dto, r);
		if (r.getCreateTime() == null)
			r.setCreateTime(LocalDateTime.now());
		Set<String> menuIds = resolveMenuIds(dto);
		if (menuIds != null) {
			Set<Menu> menus = new HashSet<>(this.menuRepository.findAllById(menuIds));
			r.setMenus(menus);
		}
		this.roleRepository.save(r);
		persistMenuPermissions(r.getId(), dto);
		persistRolePermissions(r.getId(), dto);
		persistDataScopes(r.getId(), dto);
	}

	@Transactional
	public void update(RoleDTO dto) {
		if (dto == null || dto.getId() == null)
			throw new IllegalArgumentException("id required");
		Optional<Role> opt = this.roleRepository.findById(dto.getId());
		if (!opt.isPresent())
			throw new IllegalArgumentException("role not found");
		Role r = opt.get();
		validateRole(dto, r);
		if (Boolean.TRUE.equals(r.getBuiltIn())) {
			dto.setRoleCode(r.getRoleCode());
			dto.setBuiltIn(true);
		}
		BeanCopyUtil.copyNonNullProperties(dto, r);
		r.setLastUpdateTime(LocalDateTime.now());
		Set<String> menuIds = resolveMenuIds(dto);
		if (menuIds != null) {
			Set<Menu> menus = new HashSet<>(this.menuRepository.findAllById(menuIds));
			r.setMenus(menus);
		}
		this.roleRepository.save(r);
		persistMenuPermissions(r.getId(), dto);
		persistRolePermissions(r.getId(), dto);
		persistDataScopes(r.getId(), dto);
		invalidateRoleUsers(r.getId());
		var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();auditLogService.log(auth==null?"system":auth.getName(),"ROLE_AUTHORIZATION_UPDATE","roleId="+r.getId());
	}

	@Transactional
	public void delete(String id) {
		Role role = roleRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("role not found"));
		if (Boolean.TRUE.equals(role.getBuiltIn())) throw new IllegalArgumentException("built-in role cannot be deleted");
		this.roleMenuPermissionRepository.deleteByRoleId(id);
		this.rolePermissionRepository.deleteByRoleId(id);
		this.roleDataScopeRepository.deleteByRoleId(id);
		this.roleRepository.deleteById(id);
	}

	private void validateRole(RoleDTO dto, Role current) {
		if (dto == null) throw new IllegalArgumentException("role required");
		if (current == null) {
			if (dto.getRoleName() == null || dto.getRoleName().isBlank()) {
				throw new IllegalArgumentException("role name required");
			}
			if (dto.getRoleCode() == null || dto.getRoleCode().isBlank()) {
				throw new IllegalArgumentException("role code required");
			}
		}
		if (dto.getRoleName() != null) {
			dto.setRoleName(dto.getRoleName().trim());
			if (dto.getRoleName().isEmpty()) throw new IllegalArgumentException("role name required");
		}
		String code = dto.getRoleCode();
		if (code != null) {
			code = code.trim().toUpperCase();
			if (code.isEmpty()) throw new IllegalArgumentException("role code required");
			dto.setRoleCode(code);
			Role existing = roleRepository.findByRoleCode(code);
			if (existing != null && (current == null || !existing.getId().equals(current.getId()))) {
				throw new IllegalArgumentException("role code already exists");
			}
		}
		if (dto.getDataScopes() != null) {
			Set<String> allowed = Set.of("ALL", "ORGANIZATION", "DEPARTMENT", "DEPARTMENT_AND_CHILDREN", "SELF", "CUSTOM_ORGANIZATION", "CUSTOM_DEPARTMENT", "CUSTOM_EMPLOYEE");
			for (RoleDataScopeDTO scope : dto.getDataScopes()) {
				if (scope.getScopeType() == null || !allowed.contains(scope.getScopeType())) throw new IllegalArgumentException("invalid data scope type");
			}
		}
	}

	private Set<String> resolveMenuIds(RoleDTO dto) {
		if (dto == null)
			return null;
		if (dto.getMenuIds() != null && !dto.getMenuIds().isEmpty()) {
			return dto.getMenuIds();
		}
		if (dto.getMenuPermissions() != null) {
			return (Set<String>) dto.getMenuPermissions().stream().map(RoleMenuPermissionDTO::getMenuId)
					.filter(id -> (id != null && !id.isEmpty())).collect(Collectors.toSet());
		}
		return null;
	}

	private void persistMenuPermissions(String roleId, RoleDTO dto) {
		if (roleId == null || dto == null
				|| (dto.getMenuPermissions() == null && dto.getPermissionIds() == null))
			return;
		this.roleMenuPermissionRepository.deleteByRoleId(roleId);
		List<RoleMenuPermission> relations = new ArrayList<>();
		if (dto.getMenuPermissions() != null && !dto.getMenuPermissions().isEmpty()) {
			for (RoleMenuPermissionDTO item : dto.getMenuPermissions()) {
				if (item.getMenuId() == null || item.getPermissionIds() == null)
					continue;
				for (String permId : item.getPermissionIds()) {
					if (permId == null || permId.isEmpty())
						continue;
					relations.add(new RoleMenuPermission(roleId, item.getMenuId(), permId));
				}
			}
		} else if (dto.getMenuIds() != null && dto.getPermissionIds() != null) {

			for (String menuId : dto.getMenuIds()) {
				for (String permId : dto.getPermissionIds()) {
					relations.add(new RoleMenuPermission(roleId, menuId, permId));
				}
			}
		}
		if (!relations.isEmpty())
			this.roleMenuPermissionRepository.saveAll(relations);
	}

	private void persistRolePermissions(String roleId, RoleDTO dto) {
		if (roleId == null || dto == null
				|| (dto.getPermissionIds() == null && dto.getMenuPermissions() == null)) return;
		rolePermissionRepository.deleteByRoleId(roleId);
		Set<String> permissionIds = dto.getPermissionIds();
		if ((permissionIds == null || permissionIds.isEmpty()) && dto.getMenuPermissions() != null) {
			permissionIds = dto.getMenuPermissions().stream().filter(i -> i.getPermissionIds() != null)
					.flatMap(i -> i.getPermissionIds().stream()).collect(Collectors.toSet());
		}
		if (permissionIds != null) rolePermissionRepository.saveAll(permissionIds.stream()
				.filter(id -> id != null && !id.isBlank()).map(id -> new RolePermission(roleId, id)).toList());
	}

	private void persistDataScopes(String roleId, RoleDTO dto) {
		if (roleId == null || dto == null || dto.getDataScopes() == null) return;
		roleDataScopeRepository.deleteByRoleId(roleId);
		List<RoleDataScope> values = dto.getDataScopes().stream().map(scope -> {
			RoleDataScope value = new RoleDataScope(); value.setRoleId(roleId); value.setScopeType(scope.getScopeType());
			value.setOrganizationId(scope.getOrganizationId()); value.setOrganizationUnitId(scope.getOrganizationUnitId());
			value.setEmployeeId(scope.getEmployeeId()); return value;
		}).toList();
		roleDataScopeRepository.saveAll(values);
	}

	private void invalidateRoleUsers(String roleId) {
		for (var user : adminUserRepository.findByRoles_Id(roleId)) {
			user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
			adminUserRepository.save(user);refreshTokenService.revokeAll(user.getUsername());
		}
	}
}
