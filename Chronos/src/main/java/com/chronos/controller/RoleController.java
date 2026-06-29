package com.chronos.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.model.dto.RoleDTO;
import com.chronos.model.pojo.Role;
import com.chronos.model.vo.RoleDetailVO;
import com.chronos.model.vo.RoleVO;
import com.chronos.service.iService.IRoleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
@RequestMapping({ "/admin/roles" })
public class RoleController {
	@Autowired
	private IRoleService roleService;

	@GetMapping({ "/list" })
	public ResultData<Page<Role>> list(RoleDTO dto, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {
		PageRequest pageRequest = PageRequest.of(page, size);
		Page<Role> roles = this.roleService.pageRoles(dto, (Pageable) pageRequest);
		return ResultData.<Page<Role>>builder().code("200").msg("success").data(roles).build();
	}

	@GetMapping({ "/{id}" })
	public ResultData<RoleVO> getById(@PathVariable("id") String id) {
		RoleVO vo = this.roleService.getRoleById(id);
		if (vo == null)
			return ResultData.<RoleVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<RoleVO>builder().code("200").msg("success").data(vo).build();
	}

	@GetMapping({ "/{id}/detail" })
	public ResultData<RoleDetailVO> getDetail(@PathVariable("id") String id) {
		RoleDetailVO vo = this.roleService.getRoleDetail(id);
		if (vo == null)
			return ResultData.<RoleDetailVO>builder().code("404").msg("not found").data(null).build();
		return ResultData.<RoleDetailVO>builder().code("200").msg("success").data(vo).build();
	}

	@PostMapping({ "create" })
	@ResponseStatus(HttpStatus.CREATED)
	public ResultData<Void> create(@Valid @RequestBody RoleDTO dto) {
		this.roleService.save(dto);
		return ResultData.<Void>builder().code("201").msg("created").data(null).build();
	}

	@PutMapping({ "update" })
	public ResultData<Void> update(@Valid @RequestBody RoleDTO dto) {
		this.roleService.update(dto);
		return ResultData.<Void>builder().code("200").msg("updated").data(null).build();
	}

	@DeleteMapping({ "/delete/{id}" })
	public ResultData<Void> delete(@PathVariable("id") String id) {
		this.roleService.delete(id);
		return ResultData.<Void>builder().code("200").msg("deleted").data(null).build();
	}
}
