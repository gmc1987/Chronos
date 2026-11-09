package com.chronos.controller;

import com.chronos.commons.model.PageView;
import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.AdminUserDTO;
import com.chronos.model.pojo.AdminUser;
import com.chronos.model.vo.AdminUserVO;
import com.chronos.service.iService.IAdminUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/admin/users" })
public class AdminUserController {
	@Autowired
	private IAdminUserService adminUserService;
	@Autowired
	private com.chronos.security.DirectoryDataScopeGuard dataScope;
	@Autowired
	private com.chronos.Idao.IAdminUserRepository users;

	@GetMapping({ "/list" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:user:view','iam:user:manage')")
	public ResultData<PageView<AdminUser>> list(AdminUserDTO dto, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size, Authentication authentication) {
		PageRequest pageRequest = boundedPage(page, size);
		PageView<AdminUser> users = PageView.from(
				this.adminUserService.pageUsers(dto, (Pageable) pageRequest,
						dataScope.visibleOrganizationIds(authentication)));
		return ResultData.<PageView<AdminUser>>builder().code("200").msg("success").data(users).build();
	}

	private PageRequest boundedPage(int page, int size) {
		return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
	}

	@GetMapping({ "/{id}" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:user:view','iam:user:manage')")
	public ResultData<AdminUserVO> getById(@PathVariable("id") String id, Authentication authentication) {
		requireAccount(authentication, id);
		AdminUserVO vo = this.adminUserService.getUserById(id);
		if (vo == null)
			return ResultData.<AdminUserVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<AdminUserVO>builder().code("200").msg("success").data(vo).build();
	}

	@GetMapping("/by-employee/{employeeId}")
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:user:view','iam:user:manage')")
	public ResultData<AdminUserVO> getByEmployeeId(@PathVariable String employeeId, Authentication authentication) {
		dataScope.requireEmployee(authentication, employeeId);
		AdminUserVO vo = this.adminUserService.getUserByEmployeeId(employeeId);
		return ResultData.<AdminUserVO>builder().code("200").msg("success").data(vo).build();
	}

	@PostMapping
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:user:create','iam:user:manage')")
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<Void> create(@Valid @RequestBody AdminUserDTO dto, Authentication authentication) {
		dataScope.requireOrganization(authentication, dto.getOrganizationId());
		if (dto.getEmployeeId() != null) dataScope.requireEmployee(authentication, dto.getEmployeeId());
		this.adminUserService.save(dto);
		return ResultData.<Void>builder().code("201").msg("created").data(null).build();
	}

	@PutMapping
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:user:update','iam:user:manage')")
	public ResultData<Void> update(@RequestBody AdminUserDTO dto, Authentication authentication) {
		AdminUser existing = requireAccount(authentication, dto.getId());
		if (dto.getOrganizationId() != null && !java.util.Objects.equals(
				existing.getOrganizationId(), dto.getOrganizationId()))
			throw new IllegalArgumentException("账号不允许跨机构迁移");
		dto.setOrganizationId(existing.getOrganizationId());
		this.adminUserService.update(dto);
		return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
	}

	@DeleteMapping({ "/{id}" })
	@PreAuthorize("@iamAuthorization.any(authentication, 'iam:user:disable','iam:user:manage')")
	public ResultData<Void> delete(@PathVariable("id") String id, Authentication authentication) {
		requireAccount(authentication, id);
		this.adminUserService.delete(id);
		return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
	}

	private AdminUser requireAccount(Authentication authentication, String id) {
		AdminUser account = users.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("user not found"));
		if (dataScope.fullAccess(authentication)) return account;
		if (account.getOrganizationId() != null && !account.getOrganizationId().isBlank())
			dataScope.requireOrganization(authentication, account.getOrganizationId());
		else if (account.getEmployeeId() != null)
			dataScope.requireEmployee(authentication, account.getEmployeeId());
		else
			throw new org.springframework.security.access.AccessDeniedException("无权访问未归属机构的账号");
		return account;
	}
}
