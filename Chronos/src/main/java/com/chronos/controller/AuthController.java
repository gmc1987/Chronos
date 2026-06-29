package com.chronos.controller;

import com.chronos.Idao.IAdminUserRepository;
import com.chronos.Idao.IMenuRepository;
import com.chronos.Idao.IPermissionRepository;
import com.chronos.Idao.IRoleMenuPermissionRepository;
import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.AdminUserDTO;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.pojo.BaseEntity;
import com.chronos.model.pojo.Menu;
import com.chronos.model.pojo.Permission;
import com.chronos.model.pojo.RefreshToken;
import com.chronos.model.pojo.Role;
import com.chronos.model.pojo.RoleMenuPermission;
import com.chronos.model.vo.MenuVO;
import com.chronos.model.vo.PermissionVO;
import com.chronos.model.vo.RoleVO;
import com.chronos.security.JwtUtil;
import com.chronos.service.iService.IAuditLogService;
import com.chronos.service.iService.IRefreshTokenService;
import io.jsonwebtoken.Claims;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/auth" })
public class AuthController {
	@Autowired
	private AuthenticationManager authenticationManager;
	@Autowired
	private PasswordEncoder passwordEncoder;
	@Autowired
	private JwtUtil jwtUtil;
	@Autowired
	private IAdminUserRepository adminUserRepository;
	@Autowired
	private IRoleMenuPermissionRepository roleMenuPermissionRepository;
	@Autowired
	private IPermissionRepository permissionRepository;
	@Autowired
	private IMenuRepository menuRepository;
	@Autowired
	private IRefreshTokenService refreshTokenService;
	@Autowired
	private IAuditLogService auditLogService;

	@PostMapping({ "/login" })
	@ResponseStatus(HttpStatus.OK)
	@Transactional
	public ResultData<Map<String, Object>> login(@RequestBody AdminUserDTO dto) {
		Authentication auth = this.authenticationManager.authenticate(
				(Authentication) new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword()));
		SecurityContextHolder.getContext().setAuthentication(auth);
		UserDetails user = (UserDetails) auth.getPrincipal();
		List<String> roles = (List<String>) auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
				.collect(Collectors.toList());
		Map<String, Object> claims = new HashMap<>();
		claims.put("roles", roles);
		String access = this.jwtUtil.generateAccessToken(user.getUsername(), claims);
		String refresh = this.jwtUtil.generateRefreshToken(user.getUsername(), claims);

		Claims parsed = this.jwtUtil.parseToken(refresh);
		LocalDateTime expiry = Instant.ofEpochMilli(parsed.getExpiration().getTime()).atZone(ZoneId.systemDefault())
				.toLocalDateTime();
		this.refreshTokenService.create(refresh, user.getUsername(), expiry);
		this.auditLogService.log(user.getUsername(), "LOGIN", "user logged in");

		AdminUser admin = this.adminUserRepository.findByUsername(user.getUsername());
		Set<Role> roleEntities = (admin != null) ? admin.getRoles() : new HashSet<>();

		List<RoleVO> roleVos = (List<RoleVO>) roleEntities.stream().map(
				r -> RoleVO.builder().id(r.getId()).roleName(r.getRoleName()).description(r.getDescription()).build())
				.collect(Collectors.toList());

		Set<Permission> permissionEntities = new HashSet<>();
		Set<Menu> menuEntities = new HashSet<>();
		if (!roleEntities.isEmpty()) {
			List<String> roleIds = (List<String>) roleEntities.stream().map(BaseEntity::getId)
					.collect(Collectors.toList());
			List<RoleMenuPermission> relations = this.roleMenuPermissionRepository.findByRoleIdIn(roleIds);
			Set<String> permissionIds = (Set<String>) relations.stream().map(RoleMenuPermission::getPermissionId)
					.collect(Collectors.toSet());
			Set<String> menuIds = (Set<String>) relations.stream().map(RoleMenuPermission::getMenuId)
					.collect(Collectors.toSet());
			if (!permissionIds.isEmpty()) {
				permissionEntities.addAll(this.permissionRepository.findAllById(permissionIds));
			}
			if (!menuIds.isEmpty()) {
				Set<Menu> directMenus = new HashSet<>(this.menuRepository.findAllById(menuIds));
				menuEntities.addAll(directMenus);

				menuEntities.addAll(collectParentMenus(directMenus));
			}
		}

		List<PermissionVO> permissionVos = (List<PermissionVO>) permissionEntities.stream()
				.map(p -> PermissionVO.builder().id(p.getId()).permissionName(p.getPermissionName())
						.permissionCode(p.getPermissionCode()).description(p.getDescription()).build())
				.collect(Collectors.toList());

		List<MenuVO> menuTree = buildMenuTree(menuEntities);

		Map<String, Object> data = new HashMap<>();
		data.put("accessToken", access);
		data.put("refreshToken", refresh);
		data.put("roles", roleVos);
		data.put("permissions", permissionVos);
		data.put("menus", menuTree);
		return ResultData.<Map<String, Object>>builder().code("200").msg("ok").data(data).build();
	}

	@PostMapping({ "/refresh" })
	@ResponseStatus(HttpStatus.OK)
	public ResultData<Map<String, String>> refresh(@RequestBody Map<String, String> body) {
		String refreshToken = body.get("refreshToken");
		if (refreshToken == null) {
			return ResultData.<Map<String, String>>builder().code("400").msg("refreshToken required").data(null)
					.build();
		}
		try {
			Claims claims = this.jwtUtil.parseToken(refreshToken);

			RefreshToken stored = this.refreshTokenService.findByToken(refreshToken);
			if (stored == null) {
				this.auditLogService.log(null, "REFRESH_FAIL", "refresh token not found");
				return ResultData.<Map<String, String>>builder().code("401").msg("invalid refresh token").data(null)
						.build();
			}
			if (Boolean.TRUE.equals(stored.getRevoked())) {
				this.auditLogService.log(stored.getUsername(), "REFRESH_FAIL", "refresh token revoked");
				return ResultData.<Map<String, String>>builder().code("401").msg("refresh token revoked").data(null)
						.build();
			}

			if (stored.getExpiryTime().isBefore(LocalDateTime.now())) {
				this.auditLogService.log(stored.getUsername(), "REFRESH_FAIL", "refresh token expired");
				return ResultData.<Map<String, String>>builder().code("401").msg("refresh token expired").data(null)
						.build();
			}

			String username = claims.getSubject();
			AdminUser user = this.adminUserRepository.findByUsername(username);
			if (user == null) {
				return ResultData.<Map<String, String>>builder().code("401").msg("user not found").data(null).build();
			}
			if (user.getStatus() == null || user.getStatus().intValue() != 1) {
				return ResultData.<Map<String, String>>builder().code("403").msg("user disabled").data(null).build();
			}
			Object rolesObj = claims.get("roles");
			Map<String, Object> newClaims = new HashMap<>();
			newClaims.put("roles", rolesObj);
			String access = this.jwtUtil.generateAccessToken(username, newClaims);
			this.auditLogService.log(username, "REFRESH", "refreshed access token");
			Map<String, String> data = new HashMap<>();
			data.put("accessToken", access);
			return ResultData.<Map<String, String>>builder().code("200").msg("ok").data(data).build();
		} catch (Exception e) {
			this.auditLogService.log(null, "REFRESH_FAIL", "invalid refresh token");
			return ResultData.<Map<String, String>>builder().code("401").msg("invalid refresh token").data(null)
					.build();
		}
	}

	@PostMapping({ "/revoke" })
	@ResponseStatus(HttpStatus.OK)
	public ResultData<Void> revoke(@RequestBody Map<String, String> body) {
		String refreshToken = body.get("refreshToken");
		if (refreshToken == null) {
			return ResultData.<Void>builder().code("400").msg("refreshToken required").data(null).build();
		}
		RefreshToken stored = this.refreshTokenService.findByToken(refreshToken);
		if (stored == null) {
			return ResultData.<Void>builder().code("404").msg("not found").data(null).build();
		}
		this.refreshTokenService.revoke(refreshToken);
		this.auditLogService.log(stored.getUsername(), "REVOKE", "revoked refresh token");
		return ResultData.<Void>builder().code("200").msg("revoked").data(null).build();
	}

	private Set<Menu> collectParentMenus(Set<Menu> menus) {
		if (menus == null || menus.isEmpty())
			return new HashSet<>();
		List<Menu> allMenus = this.menuRepository.findAll();
		Map<String, Menu> menuMap = (Map<String, Menu>) allMenus.stream()
				.collect(Collectors.toMap(BaseEntity::getId, m -> m));
		Set<Menu> parents = new HashSet<>();
		for (Menu menu : menus) {
			String pid = menu.getParentId();
			while (pid != null && !pid.isEmpty()) {
				Menu parent = menuMap.get(pid);
				if (parent == null || !parents.add(parent))
					break;
				pid = parent.getParentId();
			}
		}
		return parents;
	}

	private List<MenuVO> buildMenuTree(Set<Menu> menus) {
		if (menus == null || menus.isEmpty())
			return new ArrayList<>();
		Map<String, MenuVO> map = new HashMap<>();
		for (Menu m : menus) {
			MenuVO vo = MenuVO.builder().id(m.getId()).menuName(m.getMenuName()).path(m.getPath())
					.parentId(m.getParentId()).orderNum(m.getOrderNum()).build();
			map.put(vo.getId(), vo);
		}
		List<MenuVO> roots = new ArrayList<>();
		for (Menu m : menus) {
			MenuVO vo = map.get(m.getId());
			String pid = m.getParentId();
			if (pid == null || pid.isEmpty() || !map.containsKey(pid)) {
				roots.add(vo);
				continue;
			}
			MenuVO parent = map.get(pid);
			try {
				Field f = MenuVO.class.getDeclaredField("children");
				f.setAccessible(true);
				List<MenuVO> children = (List<MenuVO>) f.get(parent);
				if (children == null)
					children = new ArrayList<>();
				children.add(vo);
				f.set(parent, children);
			} catch (Exception exception) {
			}
		}

		return sortMenuTree(roots);
	}

	private List<MenuVO> sortMenuTree(List<MenuVO> roots) {
		if (roots == null)
			return roots;
		Comparator<MenuVO> cmp = (a, b) -> {
			Integer oa = Integer.valueOf((a.getOrderNum() == null) ? 0 : a.getOrderNum().intValue());
			Integer ob = Integer.valueOf((b.getOrderNum() == null) ? 0 : b.getOrderNum().intValue());
			return oa.compareTo(ob);
		};
		roots.sort(cmp);
		for (MenuVO r : roots) {
			List<MenuVO> children = r.getChildren();
			if (children != null && !children.isEmpty()) {
				children.sort(cmp);
				sortMenuTree(children);
				try {
					Field f = MenuVO.class.getDeclaredField("children");
					f.setAccessible(true);
					f.set(r, children);
				} catch (Exception exception) {
				}
			}
		}

		return roots;
	}
}
