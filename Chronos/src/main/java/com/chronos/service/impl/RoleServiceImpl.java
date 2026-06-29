package com.chronos.service.impl;

import com.chronos.Idao.IMenuRepository;
import com.chronos.Idao.IRoleMenuPermissionRepository;
import com.chronos.Idao.IRoleRepository;
import com.chronos.commons.utils.BeanCopyUtil;
import com.chronos.model.dto.RoleDTO;
import com.chronos.model.dto.RoleMenuPermissionDTO;
import com.chronos.model.pojo.Menu;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.RoleMenuPermission;
import com.chronos.model.vo.RoleDetailVO;
import com.chronos.model.vo.RoleVO;
import com.chronos.service.iService.IRoleService;
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

	public Page<Role> pageRoles(RoleDTO dto, Pageable pageable) {
		return this.roleRepository.findAll(pageable);
	}

	public RoleVO getRoleById(String id) {
		Optional<Role> opt = this.roleRepository.findById(id);
		if (!opt.isPresent())
			return null;
		Role r = opt.get();
		RoleVO vo = RoleVO.builder().id(r.getId()).roleName(r.getRoleName()).description(r.getDescription()).build();
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
		Set<String> permissionIds = relations.stream().map(RoleMenuPermission::getPermissionId)
				.collect(Collectors.toSet());

		// 4. menu -> permissionIds 映射
		Map<String, Set<String>> menuPermissionMap = relations.stream()
				.collect(Collectors.groupingBy(RoleMenuPermission::getMenuId,
						Collectors.mapping(RoleMenuPermission::getPermissionId, Collectors.toSet())));

		// 5. 转 DTO
		List<RoleMenuPermissionDTO> menuPermissions = menuPermissionMap.entrySet().stream()
				.map(entry -> new RoleMenuPermissionDTO(entry.getKey(), entry.getValue())).collect(Collectors.toList());

		// 6. 返回
		return RoleDetailVO.builder().id(r.getId()).roleName(r.getRoleName()).description(r.getDescription())
				.menuIds(menuIds).permissionIds(permissionIds).menuPermissions(menuPermissions).build();
	}

	@Transactional
	public void save(RoleDTO dto) {
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
	}

	@Transactional
	public void update(RoleDTO dto) {
		if (dto == null || dto.getId() == null)
			throw new IllegalArgumentException("id required");
		Optional<Role> opt = this.roleRepository.findById(dto.getId());
		if (!opt.isPresent())
			throw new IllegalArgumentException("role not found");
		Role r = opt.get();
		BeanCopyUtil.copyNonNullProperties(dto, r);
		r.setLastUpdateTime(LocalDateTime.now());
		Set<String> menuIds = resolveMenuIds(dto);
		if (menuIds != null) {
			Set<Menu> menus = new HashSet<>(this.menuRepository.findAllById(menuIds));
			r.setMenus(menus);
		}
		this.roleRepository.save(r);
		persistMenuPermissions(r.getId(), dto);
	}

	@Transactional
	public void delete(String id) {
		this.roleMenuPermissionRepository.deleteByRoleId(id);
		this.roleRepository.deleteById(id);
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
		if (roleId == null)
			return;
		this.roleMenuPermissionRepository.deleteByRoleId(roleId);
		List<RoleMenuPermission> relations = new ArrayList<>();
		if (dto == null)
			return;
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
}

